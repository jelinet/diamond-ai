package com.showengine.statemachine;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Transition<S, E> {
    private S from;
    private E event;
    private S to;
}
