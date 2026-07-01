package com.showengine.router.service.impl;

import com.showengine.router.enums.IntentSource;
import com.showengine.router.enums.IntentTypeEnum;
import com.showengine.enums.PlayerEnum;
import com.showengine.model.AskRequest;
import com.showengine.router.model.IntentResult;
import com.showengine.model.PlayerResponse;
import com.showengine.router.service.IntentRouterService;
import com.showengine.service.PlayerService;
import com.showengine.service.PromptTemplates;
import com.showengine.utils.PlayerFactory;
import com.showengine.utils.PlayerUtils;
import com.showengine.utils.JacksonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmIntentRouterImpl implements IntentRouterService {

    @Autowired
    private PlayerFactory playerFactory;

    @Override
    public IntentResult route(AskRequest askRequest) {
        // Use the Master Player as the intent-analysis executor.
        PlayerEnum master = PlayerUtils.listAllPlayers().stream()
                .filter(p -> p.getPlayer().equals(askRequest.getMasterPlayer()))
                .findFirst()
                .orElse(PlayerUtils.listAllPlayers().get(0));

        PlayerService masterPlayer = playerFactory.get(master);
        StringBuilder buf = new StringBuilder();

        // Call the LLM and collect the full response; intent classification is not streamed to users.
        masterPlayer.ask(askRequest.getConversationId(),
                PromptTemplates.intentRouter(askRequest.getQuestion()),
                chunk -> {
                    if (chunk.getStatus() == PlayerResponse.Status.STREAMING) {
                        buf.append(chunk.getContent());
                    }
                });

        return parseIntentResult(buf.toString().trim());
    }

    /**
     * Extracts JSON from raw LLM output and parses it into an IntentResult.
     * The LLM may wrap JSON with explanatory text, so braces are used as boundaries.
     * Falls back to UNKNOWN when parsing fails.
     */
    private IntentResult parseIntentResult(String raw) {
        try {
            // Locate JSON object boundaries to tolerate extra text before or after JSON.
            int start = raw.indexOf('{');
            int end   = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                log.warn("LlmIntentRouter：未找到 JSON，原始输出：{}", raw);
                return unknown("响应中未包含 JSON");
            }

            JsonNode node = JacksonUtil.toJsonNode(raw.substring(start, end + 1));

            // Parse the intent field and map it to the enum; invalid values fall back to UNKNOWN.
            String intentStr = node.path("intent").asText("UNKNOWN").toUpperCase();
            IntentTypeEnum intentType;
            try {
                intentType = IntentTypeEnum.valueOf(intentStr);
            } catch (IllegalArgumentException e) {
                log.warn("LlmIntentRouter：未知意图类型 {}，降级为 UNKNOWN", intentStr);
                intentType = IntentTypeEnum.UNKNOWN;
            }

            double confidence = node.path("confidence").asDouble(0.0);
            String reason     = node.path("reason").asText("");

            log.info("LlmIntentRouter：intent={} confidence={} reason={}", intentType, confidence, reason);
            return new IntentResult(intentType, confidence, reason, IntentSource.LLM, null, null);

        } catch (Exception e) {
            log.error("LlmIntentRouter：JSON 解析失败，原始输出：{}", raw, e);
            return unknown("JSON 解析失败：" + e.getMessage());
        }
    }

    private IntentResult unknown(String reason) {
        return new IntentResult(IntentTypeEnum.UNKNOWN, 0.0, reason, IntentSource.LLM, null, null);
    }


}
