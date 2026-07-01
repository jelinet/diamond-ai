package com.showengine.model;

import com.showengine.enums.PlayerEnum;
import lombok.Builder;
import lombok.Data;

/**
 * Response from a single baseball player (Pitcher / Catcher / Fielder).
 */
@Data
@Builder
public class PlayerResponse {

    public enum Status {
        /** Chunk of streamed text */
        STREAMING,
        /** Final response with token stats */
        DONE,
        /** Error occurred */
        ERROR
    }

    /** Which player this response belongs to */
    private PlayerEnum player;

    /** Current status of the response */
    private Status status;

    /** Text chunk (for STREAMING) or full response (for DONE) */
    private String content;

    /** Total input tokens consumed (populated on DONE) */
    private Integer inputTokens;

    /** Total output tokens consumed (populated on DONE) */
    private Integer outputTokens;

    /** Error message (populated on ERROR) */
    private String errorMessage;

    // ── convenience factories ──────────────────────────────────────────

    public static PlayerResponse streaming(PlayerEnum player, String chunk) {
        return PlayerResponse.builder()
                .player(player)
                .status(Status.STREAMING)
                .content(chunk)
                .build();
    }

    public static PlayerResponse done(PlayerEnum player, String fullContent,
                                      int inputTokens, int outputTokens) {
        return PlayerResponse.builder()
                .player(player)
                .status(Status.DONE)
                .content(fullContent)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .build();
    }

    public static PlayerResponse error(PlayerEnum player, String message) {
        return PlayerResponse.builder()
                .player(player)
                .status(Status.ERROR)
                .errorMessage(message)
                .build();
    }
}
