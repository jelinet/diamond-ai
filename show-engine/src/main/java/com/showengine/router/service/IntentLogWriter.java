package com.showengine.router.service;

import com.showengine.config.ShowEngineProperties;
import com.showengine.router.enums.IntentSource;
import com.showengine.router.enums.IntentTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Map;

/**
 * Writes intent recognition logs asynchronously for later offline training data collection.
 * Logs rotate daily as JSONL files under the classifier.log-dir directory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentLogWriter {

    private final ShowEngineProperties properties;
    private final ObjectMapper objectMapper;

    private Path logDir;

    @PostConstruct
    public void init() throws IOException {
        logDir = Paths.get(properties.getClassifier().getLogDir());
        Files.createDirectories(logDir);
    }

    /**
     * @param query         original user input
     * @param ruleIntent    rule-layer result, or UNKNOWN if no rule matched
     * @param ruleConf      rule-layer confidence
     * @param modelIntent   model-layer result, or UNKNOWN if the model is not ready
     * @param modelConf     model-layer confidence
     * @param llmIntent     LLM-layer result, or null if not invoked
     * @param finalIntent   final selected intent
     * @param finalSource   source layer used for the final result
     */
    @Async
    public void log(String query,
                    IntentTypeEnum ruleIntent, double ruleConf,
                    IntentTypeEnum modelIntent, double modelConf,
                    IntentTypeEnum llmIntent,
                    IntentTypeEnum finalIntent, IntentSource finalSource) {
        try {
            Map<String, Object> entry = Map.of(
                    "timestamp",   Instant.now().toString(),
                    "query",       query,
                    "rule",        Map.of("intent", ruleIntent, "confidence", ruleConf),
                    "model",       Map.of("intent", modelIntent, "confidence", modelConf),
                    "llm",         llmIntent != null ? llmIntent.name() : "N/A",
                    "final",       Map.of("intent", finalIntent, "source", finalSource)
            );

            String line = objectMapper.writeValueAsString(entry) + "\n";
            Path file = logDir.resolve(LocalDate.now() + ".jsonl");
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("意图日志写入失败：{}", e.getMessage());
        }
    }
}
