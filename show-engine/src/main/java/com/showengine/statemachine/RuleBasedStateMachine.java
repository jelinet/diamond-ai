package com.showengine.statemachine;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RuleBasedStateMachine<S, E, C> implements StateMachine<S, E, C> {

    private final Map<Key<S, E>, S> transitions = new ConcurrentHashMap<>();

    public RuleBasedStateMachine(Collection<Transition<S, E>> rules) {
        if (rules != null) {
            for (Transition<S, E> rule : rules) {
                transitions.put(new Key<>(rule.getFrom(), rule.getEvent()), rule.getTo());
            }
        }
    }

    @Override
    public TransitionResult<S> transition(S currentState, E event, C context) {
        S next = transitions.get(new Key<>(currentState, event));
        if (next == null) {
            throw new InvalidTransitionException(currentState, event);
        }

        return TransitionResult.<S>builder()
                .previousState(currentState)
                .currentState(next)
                .changed(!Objects.equals(currentState, next))
                .build();
    }

    private record Key<S, E>(S state, E event) {
    }
}
