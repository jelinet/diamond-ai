package com.showengine.supervision.model;

import com.showengine.enums.PlayerEnum;
import com.showengine.supervision.enmus.SupervisionModeEnum;
import com.showengine.router.enums.IntentTypeEnum;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SupervisionPlan {

    private IntentTypeEnum intentType;
    private SupervisionModeEnum mode;
    private List<PlayerEnum> players;
    private PlayerEnum synthesizer;
    private boolean requiresConfirmation;
    private List<String> phases;
}
