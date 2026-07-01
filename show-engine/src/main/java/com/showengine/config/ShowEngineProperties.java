package com.showengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "show-engine")
public class ShowEngineProperties {

    private Pitcher pitcher = new Pitcher();
    private Catcher catcher = new Catcher();
    private Fielder fielder = new Fielder();
    private Sessions sessions = new Sessions();
    private Classifier classifier = new Classifier();

    @Data
    public static class Pitcher {
        /** Path to the claude CLI executable */
        private String cliPath = "claude";
        /** Process timeout in seconds */
        private int timeoutSeconds = 120;
        /** Claude model to use */
        private String model = "claude-sonnet-4-5";
    }

    @Data
    public static class Catcher {
        /** Path to the gemini CLI executable */
        private String cliPath = "gemini";
        /** Process timeout in seconds */
        private int timeoutSeconds = 120;
        /** Gemini model to use */
        private String model = "gemini-3.5-flash";
    }

    @Data
    public static class Fielder {
        /** Path to the codex CLI executable */
        private String cliPath = "codex";
        /** Process timeout in seconds */
        private int timeoutSeconds = 120;
        /** Codex model to use; blank means use the CLI default */
        private String model = "";
    }

    @Data
    public static class Sessions {
        /** Session persistence directory. */
        private String dataDir = System.getProperty("user.home") + "/.show-engine/sessions";
    }

    @Data
    public static class Classifier {
        /** Whether the classifier is enabled; false skips directly to the LLM. */
        private boolean enabled = true;
        /** Classifier confidence threshold; higher values directly accept classifier results. */
        private double confidenceThreshold = 0.75;
        /** Prototype vector file path. */
        private String prototypesPath = System.getProperty("user.home") + "/.show-engine/model/v1/prototypes.json";
        /** ONNX model file path. */
        private String modelPath = System.getProperty("user.home") + "/.show-engine/model/v1/encoder.onnx";
        /** Intent log output directory. */
        private String logDir = System.getProperty("user.home") + "/.show-engine/intent-logs";
    }
}
