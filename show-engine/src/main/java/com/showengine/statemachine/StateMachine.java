package com.showengine.statemachine;

public interface StateMachine<S, E, C> {

    TransitionResult<S> transition(S currentState, E event, C context);
}
