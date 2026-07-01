package com.showengine.service;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.SubTask;

import java.util.List;

/**
 * Task decomposition service that asks the Master LLM to split a user task into subtasks.
 * Handles prompt construction and JSON parsing so callers receive a ready SubTask list.
 */
public interface DecomposeService {

    /**
     * @param conversationId conversation ID used to generate child conversation IDs
     * @param question       original user question
     * @param master         Player acting as Master
     * @return parsed subtask list; throws when parsing fails
     */
    List<SubTask> decompose(String conversationId, String question, PlayerEnum master);
}
