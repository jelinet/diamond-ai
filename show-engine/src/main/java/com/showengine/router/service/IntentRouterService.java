package com.showengine.router.service;

import com.showengine.model.AskRequest;
import com.showengine.router.model.IntentResult;

/**
 * Routes user input to an intent result.
 */
public interface IntentRouterService {

    /**
     * Routes the incoming user request.
     *
     * @param askRequest user request payload
     * @return intent routing result
     */
    IntentResult route(AskRequest askRequest);
}
