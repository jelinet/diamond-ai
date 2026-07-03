package com.showengine.supervision.service;

import com.showengine.enums.PlayerEnum;
import com.showengine.supervision.model.SupervisorContext;
import com.showengine.model.SubTask;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface SupervisorService {

    /** Executes the orchestration strategy selected from the router result. */
    void supervise(SupervisorContext context);

    /** Executes a previously decomposed task after user confirmation. */
    void executeConfirmed(SseEmitter emitter, String conversationId, String question,
                          PlayerEnum masterPlayer, List<SubTask> confirmedPlan);

    /** Cancels supervisor-owned child sessions and pending orchestration state. */
    void cancel(String conversationId);
}
