package com.showengine.supervision.model;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.AskRequest;
import com.showengine.router.model.IntentResult;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;
import java.util.function.Function;

@Data
@Builder
public class SupervisorContext {

    private String conversationId;
    private String question;
    private PlayerEnum masterPlayer;
    private AskRequest request;
    private IntentResult intentResult;
    private SseEmitter emitter;

    /** Updates the current session Master when a control command switches Player. */
    private Consumer<PlayerEnum> masterSwitcher;

    /** Starts a fresh session, usually after a clear/reset control command. */
    private Function<String, String> sessionStarter;
}
