package com.showengine.players.service;

import com.showengine.enums.PlayerEnum;
import com.showengine.players.model.PlayerResponse;

import java.util.function.Consumer;

/**
 * Contract for a baseball player (AI model) service.
 * Each implementation streams its response token-by-token
 * and delivers a final DONE event with token counts.
 */
public interface PlayerService {

    /**
     * Ask the player a question.
     * Implementations must:
     * 1. Call {@code onChunk} for each streamed text chunk.
     * 2. Call {@code onChunk} once with a DONE event (including token counts).
     * 3. Call {@code onChunk} once with an ERROR event if something fails.
     *
     * @param question the user's question
     * @param onChunk  callback invoked for each PlayerResponse event
     */
    void ask(String conversationId, String question, Consumer<PlayerResponse> onChunk);

    /**
     * Returns which player this service represents.
     */
    PlayerEnum getPlayer();

    /**
     * Cancel any in-progress CLI process for the given conversation.
     * Default is no-op for services that don't track processes.
     */
    default void cancel(String conversationId) {}
}
