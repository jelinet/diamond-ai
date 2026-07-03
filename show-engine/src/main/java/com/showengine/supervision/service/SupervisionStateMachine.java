package com.showengine.supervision.service;

import com.showengine.statemachine.RuleBasedStateMachine;
import com.showengine.statemachine.StateMachine;
import com.showengine.statemachine.Transition;
import com.showengine.statemachine.TransitionResult;
import com.showengine.supervision.enmus.SupervisionEventEnum;
import com.showengine.supervision.enmus.SupervisionStateEnum;
import com.showengine.supervision.model.SupervisorContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SupervisionStateMachine implements StateMachine<SupervisionStateEnum, SupervisionEventEnum, SupervisorContext> {

    private final RuleBasedStateMachine<SupervisionStateEnum, SupervisionEventEnum, SupervisorContext> delegate;

    public SupervisionStateMachine() {
        this.delegate = new RuleBasedStateMachine<>(rules());
    }

    @Override
    public TransitionResult<SupervisionStateEnum> transition(
            SupervisionStateEnum currentState,
            SupervisionEventEnum event,
            SupervisorContext context) {
        return delegate.transition(currentState, event, context);
    }

    private List<Transition<SupervisionStateEnum, SupervisionEventEnum>> rules() {
        return List.of(
                rule(SupervisionStateEnum.PLAN_CREATED, SupervisionEventEnum.START_DECOMPOSITION, SupervisionStateEnum.DECOMPOSING),
                rule(SupervisionStateEnum.DECOMPOSING, SupervisionEventEnum.DECOMPOSITION_READY, SupervisionStateEnum.WAITING_USER_CONFIRMATION),
                rule(SupervisionStateEnum.WAITING_USER_CONFIRMATION, SupervisionEventEnum.CONFIRM_EXECUTION, SupervisionStateEnum.RUNNING_PLAYERS),
                rule(SupervisionStateEnum.PLAN_CREATED, SupervisionEventEnum.START_PLAYERS, SupervisionStateEnum.RUNNING_PLAYERS),
                rule(SupervisionStateEnum.RUNNING_PLAYERS, SupervisionEventEnum.START_ASSEMBLY, SupervisionStateEnum.ASSEMBLING_RESULT),
                rule(SupervisionStateEnum.RUNNING_PLAYERS, SupervisionEventEnum.COMPLETE, SupervisionStateEnum.COMPLETED),
                rule(SupervisionStateEnum.ASSEMBLING_RESULT, SupervisionEventEnum.COMPLETE, SupervisionStateEnum.COMPLETED),
                rule(SupervisionStateEnum.PLAN_CREATED, SupervisionEventEnum.COMPLETE, SupervisionStateEnum.COMPLETED),

                rule(SupervisionStateEnum.PLAN_CREATED, SupervisionEventEnum.CANCEL, SupervisionStateEnum.CANCELLED),
                rule(SupervisionStateEnum.DECOMPOSING, SupervisionEventEnum.CANCEL, SupervisionStateEnum.CANCELLED),
                rule(SupervisionStateEnum.WAITING_USER_CONFIRMATION, SupervisionEventEnum.CANCEL, SupervisionStateEnum.CANCELLED),
                rule(SupervisionStateEnum.RUNNING_PLAYERS, SupervisionEventEnum.CANCEL, SupervisionStateEnum.CANCELLED),
                rule(SupervisionStateEnum.ASSEMBLING_RESULT, SupervisionEventEnum.CANCEL, SupervisionStateEnum.CANCELLED),

                rule(SupervisionStateEnum.PLAN_CREATED, SupervisionEventEnum.FAIL, SupervisionStateEnum.FAILED),
                rule(SupervisionStateEnum.DECOMPOSING, SupervisionEventEnum.FAIL, SupervisionStateEnum.FAILED),
                rule(SupervisionStateEnum.WAITING_USER_CONFIRMATION, SupervisionEventEnum.FAIL, SupervisionStateEnum.FAILED),
                rule(SupervisionStateEnum.RUNNING_PLAYERS, SupervisionEventEnum.FAIL, SupervisionStateEnum.FAILED),
                rule(SupervisionStateEnum.ASSEMBLING_RESULT, SupervisionEventEnum.FAIL, SupervisionStateEnum.FAILED)
        );
    }

    private Transition<SupervisionStateEnum, SupervisionEventEnum> rule(
            SupervisionStateEnum from,
            SupervisionEventEnum event,
            SupervisionStateEnum to) {
        return Transition.<SupervisionStateEnum, SupervisionEventEnum>builder()
                .from(from)
                .event(event)
                .to(to)
                .build();
    }
}
