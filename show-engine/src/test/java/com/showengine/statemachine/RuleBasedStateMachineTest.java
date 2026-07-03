package com.showengine.statemachine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleBasedStateMachineTest {

    enum TestStateEnum {
        PLANNED,
        RUNNING,
        COMPLETED
    }

    enum TestEventEnum {
        START,
        FINISH,
        KEEP
    }

    @Test
    void transitionReturnsNextStateForValidRule() {
        RuleBasedStateMachine<TestStateEnum, TestEventEnum, Void> machine = machine();

        TransitionResult<TestStateEnum> result = machine.transition(TestStateEnum.PLANNED, TestEventEnum.START, null);

        assertThat(result.getPreviousState()).isEqualTo(TestStateEnum.PLANNED);
        assertThat(result.getCurrentState()).isEqualTo(TestStateEnum.RUNNING);
        assertThat(result.isChanged()).isTrue();
    }

    @Test
    void transitionMarksUnchangedWhenRuleKeepsSameState() {
        RuleBasedStateMachine<TestStateEnum, TestEventEnum, Void> machine = machine();

        TransitionResult<TestStateEnum> result = machine.transition(TestStateEnum.RUNNING, TestEventEnum.KEEP, null);

        assertThat(result.getPreviousState()).isEqualTo(TestStateEnum.RUNNING);
        assertThat(result.getCurrentState()).isEqualTo(TestStateEnum.RUNNING);
        assertThat(result.isChanged()).isFalse();
    }

    @Test
    void transitionRejectsInvalidRule() {
        RuleBasedStateMachine<TestStateEnum, TestEventEnum, Void> machine = machine();

        assertThatThrownBy(() -> machine.transition(TestStateEnum.PLANNED, TestEventEnum.FINISH, null))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("state=PLANNED")
                .hasMessageContaining("event=FINISH");
    }

    private RuleBasedStateMachine<TestStateEnum, TestEventEnum, Void> machine() {
        return new RuleBasedStateMachine<>(List.of(
                Transition.<TestStateEnum, TestEventEnum>builder()
                        .from(TestStateEnum.PLANNED)
                        .event(TestEventEnum.START)
                        .to(TestStateEnum.RUNNING)
                        .build(),
                Transition.<TestStateEnum, TestEventEnum>builder()
                        .from(TestStateEnum.RUNNING)
                        .event(TestEventEnum.FINISH)
                        .to(TestStateEnum.COMPLETED)
                        .build(),
                Transition.<TestStateEnum, TestEventEnum>builder()
                        .from(TestStateEnum.RUNNING)
                        .event(TestEventEnum.KEEP)
                        .to(TestStateEnum.RUNNING)
                        .build()
        ));
    }
}
