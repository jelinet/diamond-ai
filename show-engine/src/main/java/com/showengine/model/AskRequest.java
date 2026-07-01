package com.showengine.model;

import com.showengine.enums.PlayerEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AskRequest {

    public enum Phase { INTENT, SELECT_INTENT, CONFIRM_EXECUTE, CANCEL }

    @NotBlank(message = "Question must not be blank")
    @Size(max = 4000, message = "Question must not exceed 4000 characters")
    private String question;

    private String conversationId;

    /** Which player acts as Master for this conversation (PITCHER / CATCHER / FIELDER) */
    private String masterPlayer;

    /** Current conversation phase; defaults to INTENT. */
    private Phase phase;

    /** Populated when phase=SELECT_INTENT: the intent the user chose from clarification options */
    private String selectedIntent;

    /** Populated when phase=CONFIRM_EXECUTE: the decomposition plan the user approved */
    private List<SubTask> confirmedPlan;

}
