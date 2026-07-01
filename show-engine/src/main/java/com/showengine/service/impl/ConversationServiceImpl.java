package com.showengine.service.impl;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.*;
import com.showengine.router.model.IntentResult;
import com.showengine.router.service.IntentRouterService;
import com.showengine.service.*;
import com.showengine.utils.PlayerFactory;
import com.showengine.utils.SseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final List<PlayerService> players;
    private final IntentRouterService intentRouterService;
    private final DecomposeService decomposeService;
    private final PlayerFactory playerFactory;

    // Session-level business state.
    private final ConcurrentHashMap<String, PendingPlan> pendingPlans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> conversationSubIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlayerEnum> sessionMasters = new ConcurrentHashMap<>();

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final Pattern SWITCH_PLAYER_PATTERN = Pattern.compile(
            "@(PITCHER|CATCHER|FIELDER|投手|捕手|野手)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEAR_PATTERN = Pattern.compile(
            "/clear|清空(对话|记忆|历史)|重新开始|开始新对话", Pattern.CASE_INSENSITIVE);

    private static final String OFF_TOPIC_REPLY = "你好呀，需要我帮你查代码、写点什么，还是别的？";

    @Override
    public String startSession(String masterPlayer) {
        String conversationId = UUID.randomUUID().toString();
        PlayerEnum master = resolveMaster(masterPlayer);
        sessionMasters.put(conversationId, master);
        log.info("新会话 {} 创建，master={}", conversationId, master);
        return conversationId;
    }

    @Override
    public void handle(AskRequest request, SseEmitter emitter) {
        String conversationId = request.getConversationId();
        AskRequest.Phase phase = request.getPhase() != null ? request.getPhase() : AskRequest.Phase.INTENT;
        PlayerEnum masterPlayer = getMaster(conversationId, request.getMasterPlayer());

        switch (phase) {
            case INTENT          -> detectAndRoute(emitter, conversationId, request.getQuestion(), masterPlayer, request);
            case CONFIRM_EXECUTE -> executeConfirmed(emitter, conversationId, request.getQuestion(), masterPlayer, request.getConfirmedPlan());
            case CANCEL          -> { pendingPlans.remove(conversationId); SseUtils.complete(emitter); }
            default              -> log.warn("未知 phase：{}，忽略请求", phase);
        }
    }

    @Override
    public void cancel(String conversationId) {
        cancelAll(conversationId);
    }

    @Override
    public PlayerEnum getMaster(String conversationId, String fallback) {
        return sessionMasters.getOrDefault(conversationId, resolveMaster(fallback));
    }

    // Intent detection and routing.

    private void detectAndRoute(SseEmitter emitter, String conversationId,
                                String question, PlayerEnum masterPlayer, AskRequest request) {
        IntentResult intentResult = intentRouterService.route(request);
        sendIntentEvent(emitter, intentResult);

        switch (intentResult.getIntentType()) {
            case CHAT, UNKNOWN                    -> singlePlayerFlow(emitter, conversationId, question, masterPlayer);
            case SEARCH                           -> singlePlayerFlow(emitter, conversationId, question, PlayerEnum.FIELDER);
            case CODE                             -> codeFlow(emitter, conversationId, question);
            case TRANSLATE, TECH_COMPARE,
                 TECH_DESIGN                      -> singlePlayerFlow(emitter, conversationId, question, masterPlayer);
            case DATA, WRITE, DEBATE              -> analyzeFlow(emitter, conversationId, question, masterPlayer);
            case MULTI_AGENT_TASK                 -> executeDecompose(emitter, conversationId, question, PlayerEnum.PITCHER);
            case OFF_TOPIC_CHAT                   -> offTopicResponse(emitter);
            case PLAYER_CONTROL                   -> handlePlayerControl(emitter, conversationId, question, request);
        }
    }

    // Single-Player response.

    private void singlePlayerFlow(SseEmitter emitter, String conversationId,
                                  String question, PlayerEnum player) {
        sendPhase(emitter, "answering");
        findPlayer(player.name()).ask(subId(conversationId, "single"), question,
                chunk -> SseUtils.sendEvent(emitter, chunk.getPlayer().name().toLowerCase(), chunk));
        SseUtils.sendEvent(emitter, "done", "完成");
        SseUtils.complete(emitter);
    }

    // Code mode, two stages.

    private void codeFlow(SseEmitter emitter, String conversationId, String question) {
        PlayerService pitcher = findPlayer(PlayerEnum.PITCHER.name());

        sendPhase(emitter, "analyzing");
        StringBuilder analysisBuf = new StringBuilder();
        pitcher.ask(subId(conversationId, "code-analyze"),
                PromptTemplates.codeAnalyze(question), chunk -> {
                    if (chunk.getStatus() == PlayerResponse.Status.STREAMING) {
                        // Keep analysis in a local buffer instead of streaming it to the frontend.
                        synchronized (analysisBuf) { analysisBuf.append(chunk.getContent()); }
                    } else {
                        // Forward the DONE event so the frontend can finish the analyzing phase.
                        SseUtils.sendEvent(emitter, chunk.getPlayer().name().toLowerCase(), chunk);
                    }
                });

        sendPhase(emitter, "answering");
        pitcher.ask(subId(conversationId, "code-solve"),
                PromptTemplates.codeSolve(question, analysisBuf.toString()),
                chunk -> SseUtils.sendEvent(emitter, chunk.getPlayer().name().toLowerCase(), chunk));

        SseUtils.sendEvent(emitter, "done", "完成");
        SseUtils.complete(emitter);
    }

    // Debate mode for DATA, WRITE, and DEBATE.

    private void analyzeFlow(SseEmitter emitter, String conversationId,
                             String question, PlayerEnum masterPlayer) {
        sendPhase(emitter, "round1");
        Map<String, StringBuilder> r1 = Map.of(
                "PITCHER", new StringBuilder(),
                "CATCHER", new StringBuilder(),
                "FIELDER", new StringBuilder());

        List<CompletableFuture<Void>> r1Futures = players.stream()
                .map(p -> CompletableFuture.runAsync(() -> {
                    String role = p.getPlayer().name();
                    p.ask(subId(conversationId, "r1-" + role),
                            PromptTemplates.analyzeRound1(role, question), chunk -> {
                                if (chunk.getStatus() == PlayerResponse.Status.STREAMING) {
                                    synchronized (r1.get(role)) { r1.get(role).append(chunk.getContent()); }
                                }
                                SseUtils.sendEvent(emitter, role.toLowerCase(), chunk);
                            });
                }, executor))
                .toList();
        CompletableFuture.allOf(r1Futures.toArray(new CompletableFuture[0])).join();

        String pitcherR1 = r1.get("PITCHER").toString();
        String catcherR1 = r1.get("CATCHER").toString();
        String fielderR1 = r1.get("FIELDER").toString();

        sendPhase(emitter, "round2");
        Map<String, StringBuilder> r2 = Map.of(
                "PITCHER", new StringBuilder(),
                "CATCHER", new StringBuilder(),
                "FIELDER", new StringBuilder());

        List<CompletableFuture<Void>> r2Futures = players.stream()
                .map(p -> CompletableFuture.runAsync(() -> {
                    String role = p.getPlayer().name();
                    String prompt = switch (role) {
                        case "PITCHER" -> PromptTemplates.analyzeRound2Pitcher(question, catcherR1, fielderR1);
                        case "CATCHER" -> PromptTemplates.analyzeRound2Catcher(question, pitcherR1, fielderR1);
                        default        -> PromptTemplates.analyzeRound2Fielder(question, pitcherR1, catcherR1);
                    };
                    p.ask(subId(conversationId, "r2-" + role), prompt, chunk -> {
                        if (chunk.getStatus() == PlayerResponse.Status.STREAMING) {
                            synchronized (r2.get(role)) { r2.get(role).append(chunk.getContent()); }
                        }
                        SseUtils.sendEvent(emitter, role.toLowerCase(), chunk);
                    });
                }, executor))
                .toList();
        CompletableFuture.allOf(r2Futures.toArray(new CompletableFuture[0])).join();

        sendPhase(emitter, "synthesis");
        String synthesisPrompt = PromptTemplates.analyzeSynthesis(
                question,
                pitcherR1, catcherR1, fielderR1,
                r2.get("PITCHER").toString(), r2.get("CATCHER").toString(), r2.get("FIELDER").toString());

        findPlayer(masterPlayer.name()).ask(subId(conversationId, "synthesis"), synthesisPrompt, chunk -> {
            if (chunk.getStatus() == PlayerResponse.Status.STREAMING) {
                SseUtils.sendEvent(emitter, "synthesis", Map.of("chunk", chunk.getContent()));
            }
            // Do not resend DONE as a player event; the synthesis stream already has the full content.
        });

        SseUtils.sendEvent(emitter, "done", "分析完成");
        SseUtils.complete(emitter);
    }

    // Task decomposition for TASK_PLAN.

    private void executeDecompose(SseEmitter emitter, String conversationId,
                                  String question, PlayerEnum masterPlayer) {
        sendPhase(emitter, "decomposing");
        List<SubTask> subtasks;
        try {
            subtasks = decomposeService.decompose(conversationId, question, masterPlayer);
        } catch (Exception e) {
            log.error("任务分解失败，conversationId={}", conversationId, e);
            emitter.completeWithError(e);
            return;
        }

        PendingPlan plan = PendingPlan.builder()
                .conversationId(conversationId)
                .originalQuestion(question)
                .masterPlayer(masterPlayer.name())
                .subtasks(subtasks)
                .build();
        pendingPlans.put(conversationId, plan);

        SseUtils.sendEvent(emitter, "decomposition", Map.of(
                "conversationId", plan.getConversationId(),
                "subtasks", plan.getSubtasks()));
        SseUtils.sendEvent(emitter, "awaiting_confirmation", Map.of());
        SseUtils.complete(emitter);
    }

    // Confirmed execution.

    private void executeConfirmed(SseEmitter emitter, String conversationId,
                                  String question, PlayerEnum masterPlayer, List<SubTask> confirmedPlan) {
        List<SubTask> subtasks = confirmedPlan;
        if (subtasks == null || subtasks.isEmpty()) {
            PendingPlan stored = pendingPlans.get(conversationId);
            if (stored != null) {
                subtasks = stored.getSubtasks();
                question = stored.getOriginalQuestion();
                masterPlayer = PlayerEnum.valueOf(stored.getMasterPlayer());
            }
        }
        pendingPlans.remove(conversationId);

        if (subtasks == null || subtasks.isEmpty()) {
            log.warn("会话 {} 未找到子任务", conversationId);
            SseUtils.complete(emitter);
            return;
        }

        sendPhase(emitter, "executing");

        final Map<PlayerEnum, SubTask> subtaskMap = new HashMap<>();
        for (SubTask st : subtasks) subtaskMap.put(st.getPlayer(), st);

        final Map<String, StringBuilder> results = new ConcurrentHashMap<>();
        final String finalQuestion = question;
        final PlayerEnum finalMaster = masterPlayer;

        List<CompletableFuture<Void>> execFutures = players.stream()
                .map(p -> CompletableFuture.runAsync(() -> {
                    PlayerEnum role = p.getPlayer();
                    String roleKey = role.name();
                    SubTask st = subtaskMap.get(role);
                    if (st == null) return;
                    StringBuilder roleBuf = new StringBuilder();
                    p.ask(subId(conversationId, "exec-" + roleKey),
                            PromptTemplates.executeSubtask(role, finalQuestion, st), chunk -> {
                                if (chunk.getStatus() == PlayerResponse.Status.STREAMING) {
                                    roleBuf.append(chunk.getContent());
                                }
                                SseUtils.sendEvent(emitter, roleKey.toLowerCase(), chunk);
                            });
                    results.put(roleKey, roleBuf);
                }, executor))
                .toList();
        CompletableFuture.allOf(execFutures.toArray(new CompletableFuture[0])).join();

        sendPhase(emitter, "assembling");
        String assemblePrompt = PromptTemplates.executeAssemble(
                finalQuestion,
                results.getOrDefault("PITCHER", new StringBuilder()).toString(),
                results.getOrDefault("CATCHER", new StringBuilder()).toString(),
                results.getOrDefault("FIELDER", new StringBuilder()).toString());

        findPlayer(finalMaster.name()).ask(subId(conversationId, "assemble"), assemblePrompt, chunk -> {
            if (chunk.getStatus() == PlayerResponse.Status.STREAMING) {
                SseUtils.sendEvent(emitter, "synthesis", Map.of("chunk", chunk.getContent()));
            }
            // Do not resend DONE as a player event; the synthesis stream already has the full content.
        });

        SseUtils.sendEvent(emitter, "done", "执行完成");
        SseUtils.complete(emitter);
    }

    // Intercepted intent handling.

    private void offTopicResponse(SseEmitter emitter) {
        sendPhase(emitter, "answering");
        SseUtils.sendEvent(emitter, "synthesis", Map.of("chunk", OFF_TOPIC_REPLY));
        SseUtils.sendEvent(emitter, "done", "完成");
        SseUtils.complete(emitter);
    }

    private void handlePlayerControl(SseEmitter emitter, String conversationId,
                                     String question, AskRequest request) {
        String msg;
        if (SWITCH_PLAYER_PATTERN.matcher(question).find()) {
            // Parse @XXX and switch the Master.
            var matcher = SWITCH_PLAYER_PATTERN.matcher(question);
            if (matcher.find()) {
                PlayerEnum target = resolvePlayerAlias(matcher.group(1));
                sessionMasters.put(conversationId, target);
                msg = "已切换到 " + target.name();
            } else {
                msg = "未识别到目标 Player，请使用 @PITCHER / @CATCHER / @FIELDER";
            }
        } else if (CLEAR_PATTERN.matcher(question).find()) {
            // Clear the conversation by resetting current state and opening a new session.
            cancelAll(conversationId);
            String newId = startSession(request.getMasterPlayer());
            SseUtils.sendEvent(emitter, "session", Map.of("conversationId", newId));
            msg = "对话已清空，新会话已开始";
        } else {
            // Export is not implemented yet.
            msg = "导出功能开发中，敬请期待";
        }
        sendPhase(emitter, "answering");
        SseUtils.sendEvent(emitter, "synthesis", Map.of("chunk", msg));
        SseUtils.sendEvent(emitter, "done", "完成");
        SseUtils.complete(emitter);
    }

    private PlayerEnum resolvePlayerAlias(String alias) {
        return switch (alias.toUpperCase()) {
            case "PITCHER", "投手" -> PlayerEnum.PITCHER;
            case "CATCHER", "捕手" -> PlayerEnum.CATCHER;
            case "FIELDER", "野手" -> PlayerEnum.FIELDER;
            default -> PlayerEnum.PITCHER;
        };
    }

    // Cancellation.

    private void cancelAll(String conversationId) {
        Set<String> subIds = conversationSubIds.remove(conversationId);
        if (subIds != null) {
            subIds.forEach(sid -> players.forEach(p -> p.cancel(sid)));
        }
        players.forEach(p -> p.cancel(conversationId));
        sessionMasters.remove(conversationId);
        pendingPlans.remove(conversationId);
    }

    // SSE event sending.

    private void sendPhase(SseEmitter emitter, String phase) {
        SseUtils.sendEvent(emitter, "phase", Map.of("phase", phase));
    }

    private void sendIntentEvent(SseEmitter emitter, IntentResult intentResult) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("intentType",      intentResult.getIntentType().name());
        payload.put("confidence",      intentResult.getConfidence());
        payload.put("reason",          intentResult.getReason());
        payload.put("source",          intentResult.getSource() != null ? intentResult.getSource().name() : null);
        payload.put("routedPlayers",   intentResult.getRoutedPlayers());
        payload.put("finalAnswerMode", intentResult.getFinalAnswerMode());
        SseUtils.sendEvent(emitter, "intent", payload);
    }

    // Utility methods.

    private String subId(String baseConvId, String tag) {
        String key = baseConvId + "-" + tag;
        String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        conversationSubIds.computeIfAbsent(baseConvId, k -> ConcurrentHashMap.newKeySet()).add(id);
        return id;
    }

    private PlayerService findPlayer(String playerName) {
        return players.stream()
                .filter(p -> p.getPlayer().name().equals(playerName))
                .findFirst()
                .orElse(players.get(0));
    }

    private PlayerEnum resolveMaster(String masterPlayer) {
        if (masterPlayer != null && !masterPlayer.isBlank()) {
            return PlayerEnum.fromPlayer(masterPlayer);
        }
        return PlayerEnum.PITCHER;
    }
}
