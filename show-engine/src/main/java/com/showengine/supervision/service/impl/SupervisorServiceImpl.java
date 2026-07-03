package com.showengine.supervision.service.impl;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.*;
import com.showengine.model.sse.*;
import com.showengine.players.enums.PlayerStatusEnum;
import com.showengine.players.model.PlayerResponse;
import com.showengine.players.service.PlayerService;
import com.showengine.router.enums.IntentTypeEnum;
import com.showengine.service.*;
import com.showengine.statemachine.InvalidTransitionException;
import com.showengine.supervision.enmus.SupervisionEventEnum;
import com.showengine.supervision.enmus.SupervisionStateEnum;
import com.showengine.supervision.model.SupervisionPlan;
import com.showengine.supervision.enmus.SupervisionModeEnum;
import com.showengine.supervision.model.SupervisionRunState;
import com.showengine.supervision.model.SupervisorStateEvent;
import com.showengine.supervision.model.SupervisorContext;
import com.showengine.supervision.service.SupervisionStateMachine;
import com.showengine.supervision.service.SupervisorService;
import com.showengine.utils.PromptTemplates;
import com.showengine.utils.SseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupervisorServiceImpl implements SupervisorService {

    private final List<PlayerService> players;
    private final DecomposeService decomposeService;
    private final SupervisionStateMachine supervisionStateMachine;

    private final ConcurrentHashMap<String, PendingPlan> pendingPlans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> conversationSubIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SupervisionRunState> runStates = new ConcurrentHashMap<>();

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final Pattern SWITCH_PLAYER_PATTERN = Pattern.compile(
            "@(PITCHER|CATCHER|FIELDER|投手|捕手|野手)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEAR_PATTERN = Pattern.compile(
            "/clear|清空(对话|记忆|历史)|重新开始|开始新对话", Pattern.CASE_INSENSITIVE);

    private static final String OFF_TOPIC_REPLY = "你好呀，需要我帮你查代码、写点什么，还是别的？";

    @Override
    public void supervise(SupervisorContext context) {
        SupervisionPlan plan = buildPlan(context);
        log.debug("Supervisor plan: conversationId={} intent={} mode={} players={} synthesizer={} phases={}",
                context.getConversationId(), plan.getIntentType(), plan.getMode(),
                plan.getPlayers(), plan.getSynthesizer(), plan.getPhases());
        SupervisionRunState state = SupervisionRunState.start(context.getConversationId(), plan);
        runStates.put(context.getConversationId(), state);
        sendSupervisorState(context.getEmitter(), state);

        switch (plan.getMode()) {
            case SINGLE_PLAYER              -> singlePlayerFlow(context, firstPlayer(plan, context.getMasterPlayer()));
            case TWO_PHASE_CODE             -> codeFlow(context);
            case DEBATE                     -> analyzeFlow(context);
            case DECOMPOSE_CONFIRM_EXECUTE  -> executeDecompose(context, plan.getSynthesizer());
            case OFF_TOPIC                  -> offTopicResponse(context);
            case PLAYER_CONTROL             -> handlePlayerControl(context);
        }
    }

    public SupervisionPlan buildPlan(SupervisorContext context) {
        IntentTypeEnum intent = context.getIntentResult().getIntentType();
        PlayerEnum master = context.getMasterPlayer();

        return switch (intent) {
            case CHAT, UNKNOWN -> plan(intent, SupervisionModeEnum.SINGLE_PLAYER,
                    List.of(master), master, false, List.of("answering"));
            case SEARCH -> plan(intent, SupervisionModeEnum.SINGLE_PLAYER,
                    List.of(PlayerEnum.FIELDER), PlayerEnum.FIELDER, false, List.of("answering"));
            case CODE -> plan(intent, SupervisionModeEnum.TWO_PHASE_CODE,
                    List.of(master), master, false, List.of("analyzing", "answering"));
            case TRANSLATE, TECH_COMPARE, TECH_DESIGN -> plan(intent, SupervisionModeEnum.SINGLE_PLAYER,
                    List.of(master), master, false, List.of("answering"));
            case DATA, WRITE, DEBATE -> plan(intent, SupervisionModeEnum.DEBATE,
                    List.of(PlayerEnum.PITCHER, PlayerEnum.CATCHER, PlayerEnum.FIELDER),
                    master, false, List.of("round1", "round2", "synthesis"));
            case MULTI_AGENT_TASK -> plan(intent, SupervisionModeEnum.DECOMPOSE_CONFIRM_EXECUTE,
                    List.of(PlayerEnum.PITCHER), PlayerEnum.PITCHER, true, List.of("decomposing", "awaiting_confirmation"));
            case PLAYER_CONTROL -> plan(intent, SupervisionModeEnum.PLAYER_CONTROL,
                    List.of(), master, false, List.of("answering"));
            case OFF_TOPIC_CHAT -> plan(intent, SupervisionModeEnum.OFF_TOPIC,
                    List.of(), master, false, List.of("answering"));
        };
    }

    private SupervisionPlan plan(IntentTypeEnum intentType, SupervisionModeEnum mode, List<PlayerEnum> players,
                                 PlayerEnum synthesizer, boolean requiresConfirmation, List<String> phases) {
        return SupervisionPlan.builder()
                .intentType(intentType)
                .mode(mode)
                .players(players)
                .synthesizer(synthesizer)
                .requiresConfirmation(requiresConfirmation)
                .phases(phases)
                .build();
    }

    private PlayerEnum firstPlayer(SupervisionPlan plan, PlayerEnum fallback) {
        return plan.getPlayers() == null || plan.getPlayers().isEmpty() ? fallback : plan.getPlayers().get(0);
    }

    @Override
    public void executeConfirmed(SseEmitter emitter, String conversationId,
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

        final Map<PlayerEnum, SubTask> subtaskMap = new HashMap<>();
        for (SubTask st : subtasks) subtaskMap.put(st.getPlayer(), st);

        final Map<String, StringBuilder> results = new ConcurrentHashMap<>();
        final String finalQuestion = question;
        final PlayerEnum finalMaster = masterPlayer;

        sendPhase(emitter, "executing");
        SupervisionPlan plan = plan(null, SupervisionModeEnum.DECOMPOSE_CONFIRM_EXECUTE,
                subtaskMapPlayers(subtasks), finalMaster, false, List.of("executing", "assembling"));
        SupervisionRunState state = runStates.computeIfAbsent(conversationId, id -> SupervisionRunState.start(id, plan));
        state.plan(plan);
        transitionConfirmedExecution(emitter, conversationId);
        markPhase(emitter, conversationId, "executing");

        List<CompletableFuture<Void>> execFutures = players.stream()
                .map(p -> CompletableFuture.runAsync(() -> {
                    PlayerEnum role = p.getPlayer();
                    String roleKey = role.name();
                    SubTask st = subtaskMap.get(role);
                    if (st == null) return;
                    StringBuilder roleBuf = new StringBuilder();
                    markPlayerRunning(emitter, conversationId, role);
                    askPlayer(emitter, conversationId, p, "exec-" + roleKey,
                            PromptTemplates.executeSubtask(role, finalQuestion, st), chunk -> {
                                if (chunk.getStatus() == PlayerStatusEnum.STREAMING) {
                                    roleBuf.append(chunk.getContent());
                                } else if (chunk.getStatus() == PlayerStatusEnum.ERROR) {
                                    markPlayerError(emitter, conversationId, role, chunk.getErrorMessage());
                                } else if (chunk.getStatus() == PlayerStatusEnum.DONE) {
                                    markPlayerDone(emitter, conversationId, role);
                                }
                                SseUtils.sendPlayer(emitter, chunk);
                            });
                    results.put(roleKey, roleBuf);
                }, executor))
                .toList();
        CompletableFuture.allOf(execFutures.toArray(new CompletableFuture[0])).join();

        sendPhase(emitter, "assembling");
        markPhase(emitter, conversationId, "assembling", SupervisionEventEnum.START_ASSEMBLY);
        String assemblePrompt = PromptTemplates.executeAssemble(
                finalQuestion,
                results.getOrDefault("PITCHER", new StringBuilder()).toString(),
                results.getOrDefault("CATCHER", new StringBuilder()).toString(),
                results.getOrDefault("FIELDER", new StringBuilder()).toString());

        askPlayer(emitter, conversationId, findPlayer(finalMaster.name()), "assemble", assemblePrompt, chunk -> {
            if (chunk.getStatus() == PlayerStatusEnum.STREAMING) {
                SseUtils.sendSynthesis(emitter, SynthesisEvent.builder().chunk(chunk.getContent()).build());
            } else {
                updatePlayerStateFromChunk(emitter, conversationId, chunk);
            }
        });

        SseUtils.sendDone(emitter, DoneEvent.builder().message("执行完成").build());
        transitionState(emitter, conversationId, SupervisionEventEnum.COMPLETE);
        SseUtils.complete(emitter);
    }

    @Override
    public void cancel(String conversationId) {
        Set<String> subIds = conversationSubIds.remove(conversationId);
        if (subIds != null) {
            subIds.forEach(sid -> players.forEach(p -> p.cancel(sid)));
        }
        players.forEach(p -> p.cancel(conversationId));
        pendingPlans.remove(conversationId);
        transitionState(null, conversationId, SupervisionEventEnum.CANCEL);
        runStates.remove(conversationId);
    }

    private void singlePlayerFlow(SupervisorContext context, PlayerEnum player) {
        singlePlayerFlow(context.getEmitter(), context.getConversationId(), context.getQuestion(), player);
    }

    private void singlePlayerFlow(SseEmitter emitter, String conversationId, String question, PlayerEnum player) {
        sendPhase(emitter, "answering");
        markPhase(emitter, conversationId, "answering", SupervisionEventEnum.START_PLAYERS);
        markPlayerRunning(emitter, conversationId, player);
        askPlayer(emitter, conversationId, findPlayer(player.name()), "single", question,
                chunk -> {
                    updatePlayerStateFromChunk(emitter, conversationId, chunk);
                    SseUtils.sendPlayer(emitter, chunk);
                });
        SseUtils.sendDone(emitter, DoneEvent.builder().message("完成").build());
        transitionState(emitter, conversationId, SupervisionEventEnum.COMPLETE);
        SseUtils.complete(emitter);
    }

    private void codeFlow(SupervisorContext context) {
        SseEmitter emitter = context.getEmitter();
        String conversationId = context.getConversationId();
        String question = context.getQuestion();
        PlayerService pitcher = findPlayer(PlayerEnum.PITCHER.name());

        sendPhase(emitter, "analyzing");
        markPhase(emitter, conversationId, "analyzing", SupervisionEventEnum.START_PLAYERS);
        StringBuilder analysisBuf = new StringBuilder();
        markPlayerRunning(emitter, conversationId, PlayerEnum.PITCHER);
        askPlayer(emitter, conversationId, pitcher, "code-analyze",
                PromptTemplates.codeAnalyze(question), chunk -> {
                    if (chunk.getStatus() == PlayerStatusEnum.STREAMING) {
                        synchronized (analysisBuf) { analysisBuf.append(chunk.getContent()); }
                    } else {
                        updatePlayerStateFromChunk(emitter, conversationId, chunk);
                        SseUtils.sendPlayer(emitter, chunk);
                    }
                });

        sendPhase(emitter, "answering");
        markPhase(emitter, conversationId, "answering");
        markPlayerRunning(emitter, conversationId, PlayerEnum.PITCHER);
        askPlayer(emitter, conversationId, pitcher, "code-solve",
                PromptTemplates.codeSolve(question, analysisBuf.toString()),
                chunk -> {
                    updatePlayerStateFromChunk(emitter, conversationId, chunk);
                    SseUtils.sendPlayer(emitter, chunk);
                });

        SseUtils.sendDone(emitter, DoneEvent.builder().message("完成").build());
        transitionState(emitter, conversationId, SupervisionEventEnum.COMPLETE);
        SseUtils.complete(emitter);
    }

    private void analyzeFlow(SupervisorContext context) {
        SseEmitter emitter = context.getEmitter();
        String conversationId = context.getConversationId();
        String question = context.getQuestion();
        PlayerEnum masterPlayer = context.getMasterPlayer();

        sendPhase(emitter, "round1");
        markPhase(emitter, conversationId, "round1", SupervisionEventEnum.START_PLAYERS);
        Map<String, StringBuilder> r1 = playerBuffers();

        List<CompletableFuture<Void>> r1Futures = players.stream()
                .map(p -> CompletableFuture.runAsync(() -> {
                    String role = p.getPlayer().name();
                    markPlayerRunning(emitter, conversationId, p.getPlayer());
                    askPlayer(emitter, conversationId, p, "r1-" + role,
                            PromptTemplates.analyzeRound1(role, question), chunk -> {
                                if (chunk.getStatus() == PlayerStatusEnum.STREAMING) {
                                    synchronized (r1.get(role)) { r1.get(role).append(chunk.getContent()); }
                                } else {
                                    updatePlayerStateFromChunk(emitter, conversationId, chunk);
                                }
                                SseUtils.sendPlayer(emitter, chunk);
                            });
                }, executor))
                .toList();
        CompletableFuture.allOf(r1Futures.toArray(new CompletableFuture[0])).join();

        String pitcherR1 = r1.get("PITCHER").toString();
        String catcherR1 = r1.get("CATCHER").toString();
        String fielderR1 = r1.get("FIELDER").toString();

        sendPhase(emitter, "round2");
        markPhase(emitter, conversationId, "round2");
        Map<String, StringBuilder> r2 = playerBuffers();

        List<CompletableFuture<Void>> r2Futures = players.stream()
                .map(p -> CompletableFuture.runAsync(() -> {
                    String role = p.getPlayer().name();
                    markPlayerRunning(emitter, conversationId, p.getPlayer());
                    String prompt = switch (role) {
                        case "PITCHER" -> PromptTemplates.analyzeRound2Pitcher(question, catcherR1, fielderR1);
                        case "CATCHER" -> PromptTemplates.analyzeRound2Catcher(question, pitcherR1, fielderR1);
                        default        -> PromptTemplates.analyzeRound2Fielder(question, pitcherR1, catcherR1);
                    };
                    askPlayer(emitter, conversationId, p, "r2-" + role, prompt, chunk -> {
                        if (chunk.getStatus() == PlayerStatusEnum.STREAMING) {
                            synchronized (r2.get(role)) { r2.get(role).append(chunk.getContent()); }
                        } else {
                            updatePlayerStateFromChunk(emitter, conversationId, chunk);
                        }
                        SseUtils.sendPlayer(emitter, chunk);
                    });
                }, executor))
                .toList();
        CompletableFuture.allOf(r2Futures.toArray(new CompletableFuture[0])).join();

        sendPhase(emitter, "synthesis");
        markPhase(emitter, conversationId, "synthesis", SupervisionEventEnum.START_ASSEMBLY);
        String synthesisPrompt = PromptTemplates.analyzeSynthesis(
                question,
                pitcherR1, catcherR1, fielderR1,
                r2.get("PITCHER").toString(), r2.get("CATCHER").toString(), r2.get("FIELDER").toString());

        markPlayerRunning(emitter, conversationId, masterPlayer);
        askPlayer(emitter, conversationId, findPlayer(masterPlayer.name()), "synthesis", synthesisPrompt, chunk -> {
            if (chunk.getStatus() == PlayerStatusEnum.STREAMING) {
                SseUtils.sendSynthesis(emitter, SynthesisEvent.builder().chunk(chunk.getContent()).build());
            } else {
                updatePlayerStateFromChunk(emitter, conversationId, chunk);
            }
        });

        SseUtils.sendDone(emitter, DoneEvent.builder().message("分析完成").build());
        transitionState(emitter, conversationId, SupervisionEventEnum.COMPLETE);
        SseUtils.complete(emitter);
    }

    private void executeDecompose(SupervisorContext context, PlayerEnum masterPlayer) {
        SseEmitter emitter = context.getEmitter();
        String conversationId = context.getConversationId();
        String question = context.getQuestion();

        sendPhase(emitter, "decomposing");
        markPhase(emitter, conversationId, "decomposing", SupervisionEventEnum.START_DECOMPOSITION);
        List<SubTask> subtasks;
        try {
            subtasks = decomposeService.decompose(conversationId, question, masterPlayer);
        } catch (Exception e) {
            log.error("任务分解失败，conversationId={}", conversationId, e);
            transitionState(emitter, conversationId, SupervisionEventEnum.FAIL);
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
        markPhase(emitter, conversationId, "awaiting_confirmation", SupervisionEventEnum.DECOMPOSITION_READY);

        SseUtils.sendDecomposition(emitter, DecompositionEvent.builder()
                .conversationId(plan.getConversationId())
                .subtasks(plan.getSubtasks())
                .build());
        SseUtils.sendAwaitingConfirmation(emitter, new AwaitingConfirmationEvent());
        SseUtils.complete(emitter);
    }

    private void offTopicResponse(SupervisorContext context) {
        SseEmitter emitter = context.getEmitter();
        sendPhase(emitter, "answering");
        markPhase(emitter, context.getConversationId(), "answering", SupervisionEventEnum.START_PLAYERS);
        SseUtils.sendSynthesis(emitter, SynthesisEvent.builder().chunk(OFF_TOPIC_REPLY).build());
        SseUtils.sendDone(emitter, DoneEvent.builder().message("完成").build());
        transitionState(emitter, context.getConversationId(), SupervisionEventEnum.COMPLETE);
        SseUtils.complete(emitter);
    }

    private void handlePlayerControl(SupervisorContext context) {
        SseEmitter emitter = context.getEmitter();
        String conversationId = context.getConversationId();
        String question = context.getQuestion();
        AskRequest request = context.getRequest();

        String msg;
        if (SWITCH_PLAYER_PATTERN.matcher(question).find()) {
            List<PlayerEnum> targets = extractMentionedPlayers(question);
            String cleanedQuestion = cleanMentionQuestion(question);

            if (!targets.isEmpty() && !cleanedQuestion.isBlank()) {
                Map<PlayerEnum, String> assignments = splitMentionAssignments(question);
                if (assignments.size() >= 2) {
                    mentionAssignedFlow(emitter, conversationId, assignments);
                } else if (targets.size() == 1) {
                    singlePlayerFlow(emitter, conversationId, cleanedQuestion, targets.get(0));
                } else {
                    mentionSharedFlow(emitter, conversationId, cleanedQuestion, targets);
                }
                return;
            }

            PlayerEnum target = !targets.isEmpty() ? targets.get(0) : null;
            if (target == null) {
                msg = "未识别到目标 Player，请使用 @PITCHER / @CATCHER / @FIELDER";
            } else {
                context.getMasterSwitcher().accept(target);
                SseUtils.sendMaster(emitter, MasterEvent.builder()
                        .masterPlayer(target.name())
                        .message("已切换到 " + target.name())
                        .build());
                SseUtils.sendDone(emitter, DoneEvent.builder().message("完成").build());
                SseUtils.complete(emitter);
                return;
            }
        } else if (CLEAR_PATTERN.matcher(question).find()) {
            cancel(conversationId);
            String newId = context.getSessionStarter().apply(request.getMasterPlayer());
            SseUtils.sendSession(emitter, SessionEvent.builder().conversationId(newId).build());
            msg = "对话已清空，新会话已开始";
        } else {
            msg = "导出功能开发中，敬请期待";
        }
        sendPhase(emitter, "answering");
        markPhase(emitter, conversationId, "answering", SupervisionEventEnum.START_PLAYERS);
        SseUtils.sendSynthesis(emitter, SynthesisEvent.builder().chunk(msg).build());
        SseUtils.sendDone(emitter, DoneEvent.builder().message("完成").build());
        transitionState(emitter, conversationId, SupervisionEventEnum.COMPLETE);
        SseUtils.complete(emitter);
    }

    private void mentionSharedFlow(SseEmitter emitter, String conversationId,
                                   String question, List<PlayerEnum> targets) {
        Map<PlayerEnum, String> assignments = new LinkedHashMap<>();
        for (PlayerEnum target : targets) {
            assignments.put(target, question);
        }
        mentionAssignedFlow(emitter, conversationId, assignments);
    }

    private void mentionAssignedFlow(SseEmitter emitter, String conversationId,
                                     Map<PlayerEnum, String> assignments) {
        sendPhase(emitter, "answering");
        markPhase(emitter, conversationId, "answering", SupervisionEventEnum.START_PLAYERS);
        List<CompletableFuture<Void>> futures = assignments.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> {
                    PlayerEnum player = entry.getKey();
                    String assignedQuestion = entry.getValue();
                    markPlayerRunning(emitter, conversationId, player);
                    askPlayer(emitter, conversationId, findPlayer(player.name()), "mention-" + player.name(),
                            assignedQuestion,
                            chunk -> {
                                updatePlayerStateFromChunk(emitter, conversationId, chunk);
                                SseUtils.sendPlayer(emitter, chunk);
                            });
                }, executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        SseUtils.sendDone(emitter, DoneEvent.builder().message("完成").build());
        transitionState(emitter, conversationId, SupervisionEventEnum.COMPLETE);
        SseUtils.complete(emitter);
    }

    private List<PlayerEnum> extractMentionedPlayers(String question) {
        List<PlayerEnum> targets = new ArrayList<>();
        var matcher = SWITCH_PLAYER_PATTERN.matcher(question);
        while (matcher.find()) {
            PlayerEnum target = resolvePlayerAlias(matcher.group(1));
            if (!targets.contains(target)) {
                targets.add(target);
            }
        }
        return targets;
    }

    private String cleanMentionQuestion(String question) {
        return stripMentionSeparators(SWITCH_PLAYER_PATTERN.matcher(question).replaceAll(""));
    }

    private Map<PlayerEnum, String> splitMentionAssignments(String question) {
        record Mention(PlayerEnum player, int end, int nextStart) {}

        List<Mention> mentions = new ArrayList<>();
        var matcher = SWITCH_PLAYER_PATTERN.matcher(question);
        PlayerEnum currentPlayer = null;
        int currentEnd = -1;
        while (matcher.find()) {
            if (currentPlayer != null) {
                mentions.add(new Mention(currentPlayer, currentEnd, matcher.start()));
            }
            currentPlayer = resolvePlayerAlias(matcher.group(1));
            currentEnd = matcher.end();
        }
        if (currentPlayer != null) {
            mentions.add(new Mention(currentPlayer, currentEnd, question.length()));
        }

        Map<PlayerEnum, String> assignments = new LinkedHashMap<>();
        for (Mention mention : mentions) {
            String assignedQuestion = stripMentionSeparators(question.substring(mention.end(), mention.nextStart()));
            if (assignedQuestion.isBlank()) {
                return Collections.emptyMap();
            }
            assignments.putIfAbsent(mention.player(), assignedQuestion);
        }
        return assignments.size() >= 2 ? assignments : Collections.emptyMap();
    }

    private String stripMentionSeparators(String text) {
        return text == null ? "" : text
                .replaceAll("^[\\s,，;；:：、。.!！?？-]+", "")
                .replaceAll("[\\s,，;；:：、-]+$", "")
                .trim();
    }

    private PlayerEnum resolvePlayerAlias(String alias) {
        return switch (alias.toUpperCase()) {
            case "PITCHER", "投手" -> PlayerEnum.PITCHER;
            case "CATCHER", "捕手" -> PlayerEnum.CATCHER;
            case "FIELDER", "野手" -> PlayerEnum.FIELDER;
            default -> PlayerEnum.PITCHER;
        };
    }

    private void sendPhase(SseEmitter emitter, String phase) {
        SseUtils.sendPhase(emitter, PhaseEvent.builder().phase(phase).build());
    }

    private void askPlayer(SseEmitter emitter, String conversationId, PlayerService player,
                           String subTag, String prompt, Consumer<PlayerResponse> onChunk) {
        try {
            player.ask(subId(conversationId, subTag), prompt, onChunk);
        } catch (Exception e) {
            PlayerEnum role = player.getPlayer();
            log.warn("Supervisor: player {} failed in conversation {} tag={}: {}",
                    role, conversationId, subTag, e.getMessage());
            PlayerResponse error = PlayerResponse.error(role, e.getMessage());
            updatePlayerStateFromChunk(emitter, conversationId, error);
            SseUtils.sendPlayer(emitter, error);
        }
    }

    private List<PlayerEnum> subtaskMapPlayers(List<SubTask> subtasks) {
        return subtasks.stream().map(SubTask::getPlayer).filter(Objects::nonNull).distinct().toList();
    }

    private Map<String, StringBuilder> playerBuffers() {
        Map<String, StringBuilder> buffers = new LinkedHashMap<>();
        buffers.put("PITCHER", new StringBuilder());
        buffers.put("CATCHER", new StringBuilder());
        buffers.put("FIELDER", new StringBuilder());
        return buffers;
    }

    private void markPhase(SseEmitter emitter, String conversationId, String phase) {
        SupervisionRunState state = runStates.get(conversationId);
        if (state == null) return;
        state.phase(phase);
        sendSupervisorState(emitter, state);
    }

    private void markPhase(SseEmitter emitter, String conversationId, String phase, SupervisionEventEnum event) {
        SupervisionRunState state = runStates.get(conversationId);
        if (state == null) return;
        state.phase(phase);
        transitionState(emitter, conversationId, event);
    }

    private void transitionState(SseEmitter emitter, String conversationId, SupervisionEventEnum event) {
        SupervisionRunState state = runStates.get(conversationId);
        if (state == null || event == null) return;
        try {
            state.state(supervisionStateMachine.transition(state.getState(), event, null).getCurrentState());
            sendSupervisorState(emitter, state);
        } catch (InvalidTransitionException e) {
            log.warn("Supervisor state transition rejected: conversationId={} state={} event={}",
                    conversationId, state.getState(), event);
        }
    }

    private void transitionConfirmedExecution(SseEmitter emitter, String conversationId) {
        SupervisionRunState state = runStates.get(conversationId);
        if (state == null) return;
        SupervisionEventEnum event = state.getState() == SupervisionStateEnum.WAITING_USER_CONFIRMATION
                ? SupervisionEventEnum.CONFIRM_EXECUTION
                : SupervisionEventEnum.START_PLAYERS;
        transitionState(emitter, conversationId, event);
    }

    private void markPlayerRunning(SseEmitter emitter, String conversationId, PlayerEnum player) {
        SupervisionRunState state = runStates.get(conversationId);
        if (state == null || player == null) return;
        state.player(player, PlayerStatusEnum.STREAMING);
        sendSupervisorState(emitter, state);
    }

    private void markPlayerDone(SseEmitter emitter, String conversationId, PlayerEnum player) {
        SupervisionRunState state = runStates.get(conversationId);
        if (state == null || player == null) return;
        state.player(player, PlayerStatusEnum.DONE);
        sendSupervisorState(emitter, state);
    }

    private void markPlayerError(SseEmitter emitter, String conversationId, PlayerEnum player, String errorMessage) {
        SupervisionRunState state = runStates.get(conversationId);
        if (state == null || player == null) return;
        state.error(player, errorMessage != null ? errorMessage : "unknown error");
        sendSupervisorState(emitter, state);
    }

    private void updatePlayerStateFromChunk(SseEmitter emitter, String conversationId, PlayerResponse chunk) {
        if (chunk == null || chunk.getPlayer() == null || chunk.getStatus() == null) return;
        if (chunk.getStatus() == PlayerStatusEnum.DONE) {
            markPlayerDone(emitter, conversationId, chunk.getPlayer());
        } else if (chunk.getStatus() == PlayerStatusEnum.ERROR) {
            markPlayerError(emitter, conversationId, chunk.getPlayer(), chunk.getErrorMessage());
        }
    }

    private void sendSupervisorState(SseEmitter emitter, SupervisionRunState state) {
        if (emitter == null || state == null) return;
        SseUtils.sendSupervisor(emitter, SupervisorStateEvent.builder()
                .conversationId(state.getConversationId())
                .mode(state.getPlan().getMode())
                .state(state.getState())
                .phase(state.getPhase() == null ? "" : state.getPhase())
                .players(state.getPlayerStatuses())
                .errors(state.getErrors())
                .build());
    }

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
}
