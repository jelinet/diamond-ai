package com.showengine.controller;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.AskRequest;
import com.showengine.model.CancelRequest;
import com.showengine.model.StartSessionRequest;
import com.showengine.model.StartSessionResponse;
import com.showengine.model.sse.SessionEvent;
import com.showengine.service.ConversationService;
import com.showengine.utils.SseUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AskController {

    private final ConversationService conversationService;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Valid @RequestBody AskRequest request) {
        String conversationId = (request.getConversationId() != null && !request.getConversationId().isBlank())
                ? request.getConversationId() : UUID.randomUUID().toString();
        request.setConversationId(conversationId);

        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onCompletion(() -> conversationService.cancel(conversationId));
        emitter.onError(e -> conversationService.cancel(conversationId));

        sendSession(emitter, conversationId);

        CompletableFuture.runAsync(() -> {
            try {
                conversationService.handle(request, emitter);
            } catch (Exception e) {
                log.error("会话 {} 流程异常", conversationId, e);
                emitter.completeWithError(e);
            }
        }, executor);

        return emitter;
    }

    @PostMapping("/session")
    public StartSessionResponse startSession(@RequestBody StartSessionRequest body) {
        String masterPlayer = body.getMasterPlayer();
        String conversationId = conversationService.startSession(masterPlayer);
        PlayerEnum master = conversationService.getMaster(conversationId, masterPlayer);
        return StartSessionResponse.builder()
                .conversationId(conversationId)
                .masterPlayer(master.name())
                .build();
    }

    @PostMapping("/cancel")
    public void cancel(@RequestBody CancelRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) return;
        conversationService.cancel(conversationId);
    }

    private void sendSession(SseEmitter emitter, String conversationId) {
        SseUtils.sendSession(emitter, SessionEvent.builder()
                .conversationId(conversationId)
                .build());
    }
}
