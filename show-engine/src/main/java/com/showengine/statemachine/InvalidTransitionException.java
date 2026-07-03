package com.showengine.statemachine;

public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(Object state, Object event) {
        super("Invalid state transition: state=" + state + ", event=" + event);
    }
}
