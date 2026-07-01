package com.showengine.service;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.AskRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ConversationService {

    /** Creates a new session and returns the conversationId. */
    String startSession(String masterPlayer);

    /** Dispatches by phase and pushes SSE events through the emitter. */
    void handle(AskRequest request, SseEmitter emitter);

    /** Cancels a session and all of its child sessions. */
    void cancel(String conversationId);

    /** Looks up the Master Player bound to a session. */
    PlayerEnum getMaster(String conversationId, String fallback);
}
