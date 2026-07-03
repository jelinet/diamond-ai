package com.showengine.supervision.model;

import com.showengine.enums.PlayerEnum;
import com.showengine.players.enums.PlayerStatusEnum;
import com.showengine.supervision.enmus.SupervisionModeEnum;
import com.showengine.supervision.enmus.SupervisionStateEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisorStateEvent {
    private String conversationId;
    private SupervisionModeEnum mode;
    private SupervisionStateEnum state;
    private String phase;
    private Map<PlayerEnum, PlayerStatusEnum> players;
    private Map<PlayerEnum, String> errors;
}
