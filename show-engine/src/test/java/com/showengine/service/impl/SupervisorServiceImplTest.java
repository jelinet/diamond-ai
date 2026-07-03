package com.showengine.service.impl;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.SubTask;
import com.showengine.players.model.PlayerResponse;
import com.showengine.supervision.enmus.SupervisionModeEnum;
import com.showengine.supervision.model.SupervisorContext;
import com.showengine.router.enums.IntentSourceEnum;
import com.showengine.router.enums.IntentTypeEnum;
import com.showengine.router.model.IntentResult;
import com.showengine.service.DecomposeService;
import com.showengine.players.service.PlayerService;
import com.showengine.supervision.service.SupervisionStateMachine;
import com.showengine.supervision.service.impl.SupervisorServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SupervisorServiceImplTest {

    private final SupervisorServiceImpl supervisor = new SupervisorServiceImpl(
            List.of(),
            mock(DecomposeService.class),
            new SupervisionStateMachine());

    @Test
    void searchRoutesToFielderSinglePlayerPlan() {
        var plan = supervisor.buildPlan(context(IntentTypeEnum.SEARCH, PlayerEnum.PITCHER));

        assertThat(plan.getMode()).isEqualTo(SupervisionModeEnum.SINGLE_PLAYER);
        assertThat(plan.getPlayers()).containsExactly(PlayerEnum.FIELDER);
        assertThat(plan.getSynthesizer()).isEqualTo(PlayerEnum.FIELDER);
        assertThat(plan.getPhases()).containsExactly("answering");
    }

    @Test
    void debateUsesAllPlayersAndMasterAsSynthesizer() {
        var plan = supervisor.buildPlan(context(IntentTypeEnum.DEBATE, PlayerEnum.CATCHER));

        assertThat(plan.getMode()).isEqualTo(SupervisionModeEnum.DEBATE);
        assertThat(plan.getPlayers()).containsExactly(PlayerEnum.PITCHER, PlayerEnum.CATCHER, PlayerEnum.FIELDER);
        assertThat(plan.getSynthesizer()).isEqualTo(PlayerEnum.CATCHER);
        assertThat(plan.getPhases()).containsExactly("round1", "round2", "synthesis");
    }

    @Test
    void multiAgentTaskRequiresConfirmation() {
        var plan = supervisor.buildPlan(context(IntentTypeEnum.MULTI_AGENT_TASK, PlayerEnum.FIELDER));

        assertThat(plan.getMode()).isEqualTo(SupervisionModeEnum.DECOMPOSE_CONFIRM_EXECUTE);
        assertThat(plan.isRequiresConfirmation()).isTrue();
        assertThat(plan.getPlayers()).containsExactly(PlayerEnum.PITCHER);
        assertThat(plan.getSynthesizer()).isEqualTo(PlayerEnum.PITCHER);
    }

    @Test
    void debateContinuesWhenOnePlayerThrows() {
        PlayerService pitcher = player(PlayerEnum.PITCHER, true);
        PlayerService catcher = player(PlayerEnum.CATCHER, false);
        PlayerService fielder = player(PlayerEnum.FIELDER, false);
        SupervisorServiceImpl service = new SupervisorServiceImpl(
                List.of(pitcher, catcher, fielder),
                mock(DecomposeService.class),
                new SupervisionStateMachine());

        SupervisorContext context = context(IntentTypeEnum.DEBATE, PlayerEnum.CATCHER);
        context.setEmitter(mock(SseEmitter.class));

        assertDoesNotThrow(() -> service.supervise(context));

        verify(pitcher, atLeastOnce()).ask(anyString(), anyString(), any());
        verify(catcher, atLeast(3)).ask(anyString(), anyString(), any());
        verify(fielder, atLeast(2)).ask(anyString(), anyString(), any());
    }

    @Test
    void confirmedPlanCanExecuteWithoutStoredWaitingState() {
        PlayerService pitcher = player(PlayerEnum.PITCHER, false);
        SupervisorServiceImpl service = new SupervisorServiceImpl(
                List.of(pitcher),
                mock(DecomposeService.class),
                new SupervisionStateMachine());

        assertDoesNotThrow(() -> service.executeConfirmed(
                mock(SseEmitter.class),
                "conversation-direct-confirm",
                "question",
                PlayerEnum.PITCHER,
                List.of(new SubTask(PlayerEnum.PITCHER, "implement", "code"))));

        verify(pitcher, atLeast(2)).ask(anyString(), anyString(), any());
    }

    private SupervisorContext context(IntentTypeEnum intentType, PlayerEnum master) {
        return SupervisorContext.builder()
                .conversationId("conversation-1")
                .question("question")
                .masterPlayer(master)
                .intentResult(new IntentResult(intentType, 0.9, "test", IntentSourceEnum.RULE, null, null))
                .build();
    }

    private PlayerService player(PlayerEnum player, boolean throwsOnAsk) {
        PlayerService service = mock(PlayerService.class);
        when(service.getPlayer()).thenReturn(player);
        if (throwsOnAsk) {
            doThrow(new IllegalStateException("boom"))
                    .when(service).ask(anyString(), anyString(), any());
        } else {
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<PlayerResponse> onChunk = invocation.getArgument(2, Consumer.class);
                onChunk.accept(PlayerResponse.streaming(player, player.name() + " chunk"));
                onChunk.accept(PlayerResponse.done(player, player.name() + " done", 1, 1));
                return null;
            }).when(service).ask(anyString(), anyString(), any());
        }
        return service;
    }
}
