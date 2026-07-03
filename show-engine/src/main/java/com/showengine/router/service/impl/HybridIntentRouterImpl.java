package com.showengine.router.service.impl;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.showengine.config.ShowEngineProperties;
import com.showengine.router.enums.IntentSourceEnum;
import com.showengine.router.enums.IntentTypeEnum;
import com.showengine.enums.PlayerEnum;
import com.showengine.model.AskRequest;
import com.showengine.router.model.IntentResult;
import com.showengine.router.service.IntentLogWriter;
import com.showengine.router.service.IntentRouterService;
import com.showengine.utils.JacksonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class HybridIntentRouterImpl implements IntentRouterService {

    @Autowired
    @Qualifier("ruleBasedIntentRouterImpl")
    private IntentRouterService ruleRouter;

    @Autowired
    @Qualifier("modelIntentRouterImpl")
    private IntentRouterService modelRouter;

    @Autowired
    @Qualifier("llmIntentRouterImpl")
    private IntentRouterService llmRouter;

    @Autowired
    private ShowEngineProperties properties;

    @Autowired
    private IntentLogWriter intentLogWriter;

    /** Intents intercepted by the rule layer before classifier or LLM routing. */
    private static final java.util.Set<IntentTypeEnum> INTERCEPTED = java.util.Set.of(
            IntentTypeEnum.MULTI_AGENT_TASK,
            IntentTypeEnum.PLAYER_CONTROL,
            IntentTypeEnum.OFF_TOPIC_CHAT
    );

    @Override
    public IntentResult route(AskRequest askRequest) {
        // First layer: rule matching.
        IntentResult ruleResult = ruleRouter.route(askRequest);
        IntentResult modelResult = null;
        IntentResult llmResult = null;

        // Intercepting intents return immediately and skip the second and third layers.
        if (INTERCEPTED.contains(ruleResult.getIntentType())) {
            log.info("意图路由截留：intent={}", ruleResult.getIntentType());
            IntentResult final1 = enrich(ruleResult, askRequest);
            asyncLog(askRequest.getQuestion(), ruleResult, null, null, final1);
            return final1;
        }

        if (isConfident(ruleResult)) {
            log.info("意图路由命中规则层：intent={} confidence={}", ruleResult.getIntentType(), ruleResult.getConfidence());
            IntentResult final2 = enrich(ruleResult, askRequest);
            asyncLog(askRequest.getQuestion(), ruleResult, null, null, final2);
            return final2;
        }

        // Second layer: local model.
        ShowEngineProperties.Classifier cfg = properties.getClassifier();
        if (cfg.isEnabled()) {
            modelResult = modelRouter.route(askRequest);
            if (isConfidentWith(modelResult, cfg.getConfidenceThreshold())) {
                log.info("意图路由命中模型层：intent={} confidence={}", modelResult.getIntentType(), modelResult.getConfidence());
                IntentResult final3 = enrich(modelResult, askRequest);
                asyncLog(askRequest.getQuestion(), ruleResult, modelResult, null, final3);
                return final3;
            }
        }

        // Third layer: LLM fallback.
        llmResult = llmRouter.route(askRequest);
        log.info("意图路由命中LLM层：intent={} confidence={}", llmResult.getIntentType(), llmResult.getConfidence());
        IntentResult final4 = enrich(llmResult, askRequest);
        asyncLog(askRequest.getQuestion(), ruleResult, modelResult, llmResult, final4);
        return final4;
    }

    private void asyncLog(String query, IntentResult rule, IntentResult model, IntentResult llm, IntentResult fin) {
        intentLogWriter.log(
                query,
                rule.getIntentType(), rule.getConfidence(),
                model != null ? model.getIntentType() : IntentTypeEnum.UNKNOWN,
                model != null ? model.getConfidence() : 0.0,
                llm != null ? llm.getIntentType() : null,
                fin.getIntentType(), fin.getSource()
        );
    }

    /** Populates routing decision fields owned by the service layer. */
    private IntentResult enrich(IntentResult result, AskRequest askRequest) {
        PlayerEnum masterPlayer = resolveMaster(askRequest.getMasterPlayer());
        List<PlayerEnum> routedPlayers;
        String finalAnswerMode;

        switch (result.getIntentType()) {
            case CHAT, UNKNOWN                  -> { routedPlayers = List.of(masterPlayer); finalAnswerMode = "SINGLE_PLAYER"; }
            case SEARCH                         -> { routedPlayers = List.of(PlayerEnum.FIELDER); finalAnswerMode = "SINGLE_PLAYER"; }
            case CODE                           -> { routedPlayers = List.of(PlayerEnum.PITCHER); finalAnswerMode = "TWO_PHASE"; }
            case TRANSLATE, TECH_COMPARE,
                 TECH_DESIGN                   -> { routedPlayers = List.of(masterPlayer); finalAnswerMode = "SINGLE_PLAYER"; }
            case DATA, WRITE, DEBATE            -> { routedPlayers = List.of(PlayerEnum.PITCHER, PlayerEnum.CATCHER, PlayerEnum.FIELDER); finalAnswerMode = "DEBATE"; }
            case MULTI_AGENT_TASK               -> { routedPlayers = List.of(PlayerEnum.PITCHER); finalAnswerMode = "MULTI_AGENT"; }
            // Intercepting intents do not need player routing.
            case PLAYER_CONTROL, OFF_TOPIC_CHAT -> { routedPlayers = List.of(); finalAnswerMode = "NONE"; }
            default                             -> { routedPlayers = List.of(masterPlayer); finalAnswerMode = "SINGLE_PLAYER"; }
        }

        result.setRoutedPlayers(routedPlayers);
        result.setFinalAnswerMode(finalAnswerMode);
        return result;
    }

    private boolean isConfident(IntentResult result) {
        return result.getIntentType() != IntentTypeEnum.UNKNOWN && result.getConfidence() >= 0.75;
    }

    private boolean isConfidentWith(IntentResult result, double threshold) {
        return result.getIntentType() != IntentTypeEnum.UNKNOWN && result.getConfidence() >= threshold;
    }

    private PlayerEnum resolveMaster(String masterPlayer) {
        if (masterPlayer != null && !masterPlayer.isBlank()) {
            try {
                return PlayerEnum.fromPlayer(masterPlayer);
            } catch (Exception ignored) {}
        }
        return PlayerEnum.PITCHER;
    }

    /**
     * Local-model intent routing, the second layer of the three-stage funnel.
     * Classifies with bge-small-zh encoder embeddings and prototype-vector cosine similarity.
     * Falls back to the LLM when model files are missing or fail to load.
     */
    @Slf4j
    @Service("modelIntentRouterImpl")
    @RequiredArgsConstructor
    public static class ModelIntentRouterImpl implements IntentRouterService {

        private final ShowEngineProperties properties;
        private HuggingFaceTokenizer tokenizer;
        private OrtEnvironment ortEnv;
        private OrtSession ortSession;
        private String outputTensorName;
        /** Intent label to L2-normalized prototype vector. */
        private Map<String, float[]> prototypeVectors;
        private boolean ready = false;

        @PostConstruct
        public void init() {
            ShowEngineProperties.Classifier cfg = properties.getClassifier();
            if (!cfg.isEnabled()) {
                log.info("ModelIntentRouter：分类器已禁用，跳过加载");
                return;
            }
            try {
                // Load intent prototype vectors.
                prototypeVectors = loadPrototypes(cfg.getPrototypesPath());
                log.info("ModelIntentRouter：加载 {} 个意图原型向量", prototypeVectors.size());

                // tokenizer.json lives under the tokenizer directory next to encoder.onnx.
                var tokenizerJson = Paths.get(cfg.getModelPath()).getParent().resolve("tokenizer/tokenizer.json");
                tokenizer = HuggingFaceTokenizer.newInstance(tokenizerJson, Map.of(
                        "maxLength", "128",
                        "truncation", "true",
                        "padding", "true"
                ));

                // Load ONNX with single-thread inference, which is enough for short intent text.
                ortEnv = OrtEnvironment.getEnvironment();
                var sessionOpts = new OrtSession.SessionOptions();
                sessionOpts.setIntraOpNumThreads(1);
                ortSession = ortEnv.createSession(cfg.getModelPath(), sessionOpts);

                // Use the first output tensor, usually last_hidden_state.
                outputTensorName = ortSession.getOutputNames().iterator().next();
                log.info("ModelIntentRouter：ONNX 输入={} 输出={}",
                        ortSession.getInputNames(), ortSession.getOutputNames());

                ready = true;
                log.info("ModelIntentRouter：初始化完成");
            } catch (Exception e) {
                log.warn("ModelIntentRouter：初始化失败，将跳过模型层：{}", e.getMessage());
            }
        }

        @PreDestroy
        public void destroy() {
            try { if (ortSession != null) ortSession.close(); } catch (Exception ignored) {}
            try { if (ortEnv != null) ortEnv.close(); } catch (Exception ignored) {}
            try { if (tokenizer != null) tokenizer.close(); } catch (Exception ignored) {}
        }

        @Override
        public IntentResult route(AskRequest askRequest) {
            if (!ready) {
                return fallback("模型未就绪");
            }
            long start = System.currentTimeMillis();
            try {
                // Tokenize, encode, then score with cosine similarity.
                Encoding enc = tokenizer.encode(askRequest.getQuestion());
                float[] embedding = l2Normalize(encode(enc.getIds(), enc.getAttentionMask()));

                String bestLabel = null;
                double bestScore = -1.0;
                for (var entry : prototypeVectors.entrySet()) {
                    double score = dotProduct(embedding, entry.getValue());
                    if (score > bestScore) {
                        bestScore = score;
                        bestLabel = entry.getKey();
                    }
                }

                log.debug("ModelIntentRouter：intent={} confidence={} latency={}ms",
                        bestLabel, String.format("%.4f", bestScore), System.currentTimeMillis() - start);

                return new IntentResult(
                        IntentTypeEnum.valueOf(bestLabel), bestScore, "本地模型分类",
                        IntentSourceEnum.MODEL, null, null);

            } catch (Exception e) {
                log.warn("ModelIntentRouter：推理失败，降级 UNKNOWN：{}", e.getMessage());
                return fallback("推理异常");
            }
        }

        /** Runs ONNX inference and returns the [CLS] token vector, usually 384 dimensions. */
        private float[] encode(long[] inputIds, long[] attentionMask) throws OrtException {
            try (var idsTensor   = OnnxTensor.createTensor(ortEnv, new long[][]{inputIds});
                 var maskTensor  = OnnxTensor.createTensor(ortEnv, new long[][]{attentionMask})) {

                Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
                inputs.put("input_ids", idsTensor);
                inputs.put("attention_mask", maskTensor);

                if (ortSession.getInputNames().contains("token_type_ids")) {
                    long[] tokenTypeIds = new long[inputIds.length];
                    try (var typeTensor = OnnxTensor.createTensor(ortEnv, new long[][]{tokenTypeIds})) {
                        inputs.put("token_type_ids", typeTensor);
                        try (var result = ortSession.run(inputs)) {
                            return clsEmbedding(result);
                        }
                    }
                }

                try (var result = ortSession.run(inputs)) {
                    return clsEmbedding(result);
                }
            }
        }

        private float[] clsEmbedding(OrtSession.Result result) throws OrtException {
            // last_hidden_state shape: [1, seq_len, 384]; [CLS] is token 0.
            float[][][] hiddenState = (float[][][]) result.get(outputTensorName).get().getValue();
            return hiddenState[0][0];
        }

        private Map<String, float[]> loadPrototypes(String path) throws Exception {
            JsonNode root = JacksonUtil.toJsonNode(java.nio.file.Files.readString(Paths.get(path)));
            Map<String, float[]> map = new LinkedHashMap<>();
            for (JsonNode intent : root.get("intents")) {
                String label = intent.get("label").asText();
                JsonNode vec = intent.get("vector");
                float[] v = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) v[i] = (float) vec.get(i).asDouble();
                map.put(label, v);
            }
            return map;
        }

        private float[] l2Normalize(float[] v) {
            double norm = 0;
            for (float x : v) norm += (double) x * x;
            norm = Math.sqrt(norm);
            if (norm < 1e-8) return v;
            float[] r = new float[v.length];
            for (int i = 0; i < v.length; i++) r[i] = (float) (v[i] / norm);
            return r;
        }

        private double dotProduct(float[] a, float[] b) {
            double sum = 0;
            for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
            return sum;
        }

        private IntentResult fallback(String reason) {
            return new IntentResult(IntentTypeEnum.UNKNOWN, 0.0, reason, IntentSourceEnum.MODEL, null, null);
        }
    }
}
