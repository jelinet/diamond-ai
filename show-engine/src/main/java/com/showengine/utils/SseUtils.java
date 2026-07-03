package com.showengine.utils;

import com.showengine.model.sse.AwaitingConfirmationEvent;
import com.showengine.model.sse.DecompositionEvent;
import com.showengine.model.sse.DoneEvent;
import com.showengine.model.sse.MasterEvent;
import com.showengine.model.sse.PhaseEvent;
import com.showengine.model.sse.SessionEvent;
import com.showengine.model.sse.SynthesisEvent;
import com.showengine.players.model.PlayerResponse;
import com.showengine.supervision.model.SupervisorStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class SseUtils {

    public static void sendSession(SseEmitter emitter, SessionEvent payload) {
        sendJson(emitter, "session", JacksonUtil.toJsonStrWithEmptyDefault(payload));
    }

    public static void sendPhase(SseEmitter emitter, PhaseEvent payload) {
        sendJson(emitter, "phase", JacksonUtil.toJsonStrWithEmptyDefault(payload));
    }

    public static void sendSynthesis(SseEmitter emitter, SynthesisEvent payload) {
        sendJson(emitter, "synthesis", JacksonUtil.toJsonStrWithEmptyDefault(payload));
    }

    public static void sendDecomposition(SseEmitter emitter, DecompositionEvent payload) {
        sendJson(emitter, "decomposition", JacksonUtil.toJsonStrWithEmptyDefault(payload));
    }

    public static void sendAwaitingConfirmation(SseEmitter emitter, AwaitingConfirmationEvent payload) {
        sendJson(emitter, "awaiting_confirmation", JacksonUtil.toJsonStrWithEmptyDefault(payload));
    }

    public static void sendMaster(SseEmitter emitter, MasterEvent payload) {
        sendJson(emitter, "master", JacksonUtil.toJsonStrWithEmptyDefault(payload));
    }

    public static void sendSupervisor(SseEmitter emitter, SupervisorStateEvent payload) {
        sendJson(emitter, "supervisor", JacksonUtil.toJsonStrWithEmptyDefault(payload));
    }

    public static void sendPlayer(SseEmitter emitter, PlayerResponse payload) {
        if (payload == null || payload.getPlayer() == null) {
            return;
        }
        sendJson(emitter, payload.getPlayer().name().toLowerCase(), JacksonUtil.toJsonStrWithEmptyDefault(payload));
    }

    public static void sendDone(SseEmitter emitter, DoneEvent payload) {
        sendJson(emitter, "done", JacksonUtil.toJsonStrWithEmptyDefault(payload));
    }

    private static void sendJson(SseEmitter emitter, String eventName, String jsonPayload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(jsonPayload));
        } catch (Exception e) {
            log.warn("SSE 发送失败 event={}：{}", eventName, e.getMessage());
        }
    }

    public static void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE complete 失败：{}", e.getMessage());
        }
    }

    private SseUtils() {}
}
