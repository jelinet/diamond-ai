package com.showengine.supervision.service;

import com.showengine.statemachine.InvalidTransitionException;
import com.showengine.supervision.enmus.SupervisionEventEnum;
import com.showengine.supervision.enmus.SupervisionStateEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupervisionStateMachineTest {

    private final SupervisionStateMachine machine = new SupervisionStateMachine();

    @Test
    void singlePlayerFlowCanRunAndComplete() {
        var running = machine.transition(
                SupervisionStateEnum.PLAN_CREATED,
                SupervisionEventEnum.START_PLAYERS,
                null);

        assertThat(running.getCurrentState()).isEqualTo(SupervisionStateEnum.RUNNING_PLAYERS);

        var completed = machine.transition(
                running.getCurrentState(),
                SupervisionEventEnum.COMPLETE,
                null);

        assertThat(completed.getCurrentState()).isEqualTo(SupervisionStateEnum.COMPLETED);
    }

    @Test
    void multiAgentTaskWaitsForConfirmationBeforeRunningPlayers() {
        var decomposing = machine.transition(
                SupervisionStateEnum.PLAN_CREATED,
                SupervisionEventEnum.START_DECOMPOSITION,
                null);
        var waiting = machine.transition(
                decomposing.getCurrentState(),
                SupervisionEventEnum.DECOMPOSITION_READY,
                null);
        var running = machine.transition(
                waiting.getCurrentState(),
                SupervisionEventEnum.CONFIRM_EXECUTION,
                null);

        assertThat(decomposing.getCurrentState()).isEqualTo(SupervisionStateEnum.DECOMPOSING);
        assertThat(waiting.getCurrentState()).isEqualTo(SupervisionStateEnum.WAITING_USER_CONFIRMATION);
        assertThat(running.getCurrentState()).isEqualTo(SupervisionStateEnum.RUNNING_PLAYERS);
    }

    @Test
    void runningPlayersCanMoveToAssemblyThenComplete() {
        var assembling = machine.transition(
                SupervisionStateEnum.RUNNING_PLAYERS,
                SupervisionEventEnum.START_ASSEMBLY,
                null);
        var completed = machine.transition(
                assembling.getCurrentState(),
                SupervisionEventEnum.COMPLETE,
                null);

        assertThat(assembling.getCurrentState()).isEqualTo(SupervisionStateEnum.ASSEMBLING_RESULT);
        assertThat(completed.getCurrentState()).isEqualTo(SupervisionStateEnum.COMPLETED);
    }

    @Test
    void cancellableActiveStatesMoveToCancelled() {
        assertThat(machine.transition(SupervisionStateEnum.PLAN_CREATED, SupervisionEventEnum.CANCEL, null).getCurrentState())
                .isEqualTo(SupervisionStateEnum.CANCELLED);
        assertThat(machine.transition(SupervisionStateEnum.DECOMPOSING, SupervisionEventEnum.CANCEL, null).getCurrentState())
                .isEqualTo(SupervisionStateEnum.CANCELLED);
        assertThat(machine.transition(SupervisionStateEnum.WAITING_USER_CONFIRMATION, SupervisionEventEnum.CANCEL, null).getCurrentState())
                .isEqualTo(SupervisionStateEnum.CANCELLED);
        assertThat(machine.transition(SupervisionStateEnum.RUNNING_PLAYERS, SupervisionEventEnum.CANCEL, null).getCurrentState())
                .isEqualTo(SupervisionStateEnum.CANCELLED);
        assertThat(machine.transition(SupervisionStateEnum.ASSEMBLING_RESULT, SupervisionEventEnum.CANCEL, null).getCurrentState())
                .isEqualTo(SupervisionStateEnum.CANCELLED);
    }

    @Test
    void activeStatesCanFail() {
        assertThat(machine.transition(SupervisionStateEnum.PLAN_CREATED, SupervisionEventEnum.FAIL, null).getCurrentState())
                .isEqualTo(SupervisionStateEnum.FAILED);
        assertThat(machine.transition(SupervisionStateEnum.RUNNING_PLAYERS, SupervisionEventEnum.FAIL, null).getCurrentState())
                .isEqualTo(SupervisionStateEnum.FAILED);
        assertThat(machine.transition(SupervisionStateEnum.ASSEMBLING_RESULT, SupervisionEventEnum.FAIL, null).getCurrentState())
                .isEqualTo(SupervisionStateEnum.FAILED);
    }

    @Test
    void completedStateRejectsFurtherExecutionEvents() {
        assertThatThrownBy(() -> machine.transition(
                SupervisionStateEnum.COMPLETED,
                SupervisionEventEnum.START_PLAYERS,
                null))
                .isInstanceOf(InvalidTransitionException.class);
    }
}
