package com.showengine.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class SseUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(MAPPER.writeValueAsString(payload)));
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
