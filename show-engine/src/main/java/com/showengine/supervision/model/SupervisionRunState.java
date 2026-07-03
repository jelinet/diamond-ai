package com.showengine.supervision.model;

import com.showengine.enums.PlayerEnum;
import com.showengine.players.enums.PlayerStatusEnum;
import com.showengine.supervision.enmus.SupervisionStateEnum;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Data
@Builder
public class SupervisionRunState {

    private String conversationId;
    private SupervisionPlan plan;
    private SupervisionStateEnum state;
    private String phase;
    private Instant startedAt;
    private Instant updatedAt;
    private Map<PlayerEnum, PlayerStatusEnum> playerStatuses;
    private Map<PlayerEnum, String> errors;

    public static SupervisionRunState start(String conversationId, SupervisionPlan plan) {
        Map<PlayerEnum, PlayerStatusEnum> statuses = new EnumMap<>(PlayerEnum.class);
        if (plan.getPlayers() != null) {
            for (PlayerEnum player : plan.getPlayers()) {
                statuses.put(player, PlayerStatusEnum.PENDING);
            }
        }
        Instant now = Instant.now();
        return SupervisionRunState.builder()
                .conversationId(conversationId)
                .plan(plan)
                .state(SupervisionStateEnum.PLAN_CREATED)
                .startedAt(now)
                .updatedAt(now)
                .playerStatuses(statuses)
                .errors(new EnumMap<>(PlayerEnum.class))
                .build();
    }

    public void phase(String phase) {
        this.phase = phase;
        this.updatedAt = Instant.now();
    }

    public void state(SupervisionStateEnum state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public void plan(SupervisionPlan plan) {
        this.plan = plan;
        if (plan != null && plan.getPlayers() != null) {
            for (PlayerEnum player : plan.getPlayers()) {
                this.playerStatuses.putIfAbsent(player, PlayerStatusEnum.PENDING);
            }
        }
        this.updatedAt = Instant.now();
    }

    public void player(PlayerEnum player, PlayerStatusEnum status) {
        this.playerStatuses.put(player, status);
        this.updatedAt = Instant.now();
    }

    public void error(PlayerEnum player, String message) {
        this.playerStatuses.put(player, PlayerStatusEnum.ERROR);
        this.errors.put(player, message);
        this.updatedAt = Instant.now();
    }
}
