package com.showengine.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Decomposition plan waiting for user confirmation.
 * Stored in AskController until the user approves or cancels.
 */
@Data
@Builder
public class PendingPlan {

    private String conversationId;
    private String originalQuestion;
    private String masterPlayer;
    private List<SubTask> subtasks;

    /** Used to expire stale plans (e.g. user navigated away) */
    @Builder.Default
    private Instant createdAt = Instant.now();
}
