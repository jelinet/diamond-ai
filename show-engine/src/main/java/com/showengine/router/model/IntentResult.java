package com.showengine.router.model;

import com.showengine.router.enums.IntentSource;
import com.showengine.router.enums.IntentTypeEnum;
import com.showengine.enums.PlayerEnum;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class IntentResult {

    /**
     * Intent category.
     */
    private IntentTypeEnum intentType;

    /**
     * Intent confidence.
     */
    private double confidence;

    /**
     * Classification reason.
     */
    private String reason;
    /** Layer that produced the decision. */
    private IntentSource source;
    /** Target Players selected by routing. */
    private List<PlayerEnum> routedPlayers;
    /** Answer mode: SINGLE_PLAYER / TWO_PHASE / DEBATE / TASK_PLAN. */
    private String finalAnswerMode;

    public IntentResult(IntentTypeEnum intentType, double confidence, String reason) {
        this.intentType = intentType;
        this.confidence = confidence;
        this.reason = reason;
    }

    public IntentResult(IntentTypeEnum intentType, double confidence, String reason,
                        IntentSource source, List<PlayerEnum> routedPlayers, String finalAnswerMode) {
        this.intentType = intentType;
        this.confidence = confidence;
        this.reason = reason;
        this.source = source;
        this.routedPlayers = routedPlayers;
        this.finalAnswerMode = finalAnswerMode;
    }
}
