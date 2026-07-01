package com.showengine.router.enums;

import lombok.Getter;

@Getter
public enum IntentTypeEnum {

    // Inside the three-stage funnel; these need classifier training data.
    CHAT("chat"),             // General conversation
    SEARCH("search"),         // Time-sensitive or external real-time information
    CODE("code"),             // Fix or explain concrete code or errors
    WRITE("write"),           // Pure text generation
    DATA("data"),             // Data statistics, analysis, charts, or calculations
    DEBATE("debate"),         // Open-ended value judgment, not technical convergence
    TECH_COMPARE("techCompare"),  // Compare or choose between technical terms/options
    TECH_DESIGN("techDesign"),    // Architecture or solution design requiring technical tradeoffs
    TRANSLATE("translate"),   // Translation request
    UNKNOWN("unknown"),       // Truly unclear or outside business scope

    // Directly intercepted by the rule layer before classifier routing.
    MULTI_AGENT_TASK("multiAgentTask"),  // Trigger multi-Player orchestration
    PLAYER_CONTROL("playerControl"),     // Clear conversation, switch Player, or export
    OFF_TOPIC_CHAT("offTopicChat"),      // Greeting or small talk with a fixed follow-up template
    ;

    private final String intent;

    IntentTypeEnum(String intent) {
        this.intent = intent;
    }
}
