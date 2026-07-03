package com.showengine.service.impl;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.AskRequest;
import com.showengine.supervision.model.SupervisorContext;
import com.showengine.router.model.IntentResult;
import com.showengine.router.service.IntentRouterService;
import com.showengine.service.ConversationService;
import com.showengine.supervision.service.SupervisorService;
import com.showengine.utils.SseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final IntentRouterService intentRouterService;
    private final SupervisorService supervisorService;

    private final ConcurrentHashMap<String, PlayerEnum> sessionMasters = new ConcurrentHashMap<>();

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
        AskRequest.PhaseEnum phase = request.getPhase() != null ? request.getPhase() : AskRequest.PhaseEnum.INTENT;
        PlayerEnum masterPlayer = getMaster(conversationId, request.getMasterPlayer());

        switch (phase) {
            case INTENT          -> detectAndRoute(emitter, conversationId, request.getQuestion(), masterPlayer, request);
            case CONFIRM_EXECUTE -> supervisorService.executeConfirmed(
                    emitter, conversationId, request.getQuestion(), masterPlayer, request.getConfirmedPlan());
            case CANCEL          -> { supervisorService.cancel(conversationId); SseUtils.complete(emitter); }
            default              -> log.warn("未知 phase：{}，忽略请求", phase);
        }
    }

    @Override
    public void cancel(String conversationId) {
        supervisorService.cancel(conversationId);
        sessionMasters.remove(conversationId);
    }

    @Override
    public PlayerEnum getMaster(String conversationId, String fallback) {
        return sessionMasters.getOrDefault(conversationId, resolveMaster(fallback));
    }

    private void detectAndRoute(SseEmitter emitter, String conversationId,
                                String question, PlayerEnum masterPlayer, AskRequest request) {
        IntentResult intentResult = intentRouterService.route(request);

        supervisorService.supervise(SupervisorContext.builder()
                .conversationId(conversationId)
                .question(question)
                .masterPlayer(masterPlayer)
                .request(request)
                .intentResult(intentResult)
                .emitter(emitter)
                .masterSwitcher(target -> sessionMasters.put(conversationId, target))
                .sessionStarter(this::startSession)
                .build());
    }

    private PlayerEnum resolveMaster(String masterPlayer) {
        if (masterPlayer != null && !masterPlayer.isBlank()) {
            return PlayerEnum.fromPlayer(masterPlayer);
        }
        return PlayerEnum.PITCHER;
    }
}
