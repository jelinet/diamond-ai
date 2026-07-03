package com.showengine.statemachine;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransitionResult<S> {
    private S previousState;
    private S currentState;
    private boolean changed;
}
