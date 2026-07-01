package com.showengine.service.impl;

import com.showengine.config.ShowEngineProperties;
import com.showengine.enums.PlayerEnum;
import com.showengine.model.PlayerResponse;
import com.showengine.service.PlayerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class FielderServiceImpl implements PlayerService {

    private final ShowEngineProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, String> codexSessionIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public void cancel(String conversationId) {
        Process p = activeProcesses.remove(conversationId);
        if (p != null) {
            p.destroyForcibly();
            log.debug("Fielder: cancelled process for conversation {}", conversationId);
        }
    }

    @Override
    public PlayerEnum getPlayer() {
        return PlayerEnum.FIELDER;
    }

    @Override
    public void ask(String conversationId, String question, Consumer<PlayerResponse> onChunk) {
        ShowEngineProperties.Fielder cfg = props.getFielder();
        askReal(conversationId, question, onChunk, cfg);
    }

    private void askReal(String conversationId, String question, Consumer<PlayerResponse> onChunk, ShowEngineProperties.Fielder cfg) {
        List<String> command = new ArrayList<>();
        command.add(cfg.getCliPath());
        command.add("exec");
        command.add("--json");
        command.add("--skip-git-repo-check");
        if (cfg.getModel() != null && !cfg.getModel().isBlank()) {
            command.add("--model");
            command.add(cfg.getModel());
        }
        String conversationKey = (conversationId != null && !conversationId.isBlank()) ? conversationId : "default";
        String codexSessionId = codexSessionIds.get(conversationKey);
        if (codexSessionId != null && !codexSessionId.isBlank()) {
            command.add("resume");
            command.add(codexSessionId);
        }
        command.add(question);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        log.info("Fielder → [{}]\n{}", conversationKey, question);

        int inputTokens = 0;
        int outputTokens = 0;
        StringBuilder fullContent = new StringBuilder();

        try {
            Process process = pb.start();
            activeProcesses.put(conversationId, process);
            process.getOutputStream().close();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        String type = node.path("type").asText();

                        if ("thread.started".equals(type)) {
                            String threadId = node.path("thread_id").asText("");
                            if (!threadId.isBlank()) {
                                codexSessionIds.put(conversationKey, threadId);
                            }
                        } else if ("item.completed".equals(type)
                                && "agent_message".equals(node.path("item").path("type").asText())) {
                            String text = node.path("item").path("text").asText("");
                            if (!text.isEmpty()) {
                                fullContent.append(text);
                                onChunk.accept(PlayerResponse.streaming(getPlayer(), text));
                            }
                        } else if ("turn.completed".equals(type)) {
                            JsonNode usage = node.path("usage");
                            inputTokens = usage.path("input_tokens").asInt();
                            outputTokens = usage.path("output_tokens").asInt();
                        } else if ("turn.started".equals(type) || "item.started".equals(type)) {
                            // Normal lifecycle events require no handling.
                        } else {
                            log.debug("Fielder: ignoring event type={}", type);
                        }
                    } catch (Exception e) {
                        log.debug("Fielder: failed to parse line: {}", line);
                    }
                }
            }

            boolean finished = process.waitFor(cfg.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                onChunk.accept(PlayerResponse.error(getPlayer(), "Codex CLI timed out"));
                return;
            }

            int exit = process.exitValue();
            if (exit != 0) {
                String stderr = new String(process.getErrorStream().readAllBytes());
                log.error("Codex CLI exited with code {}: {}", exit, stderr);
                onChunk.accept(PlayerResponse.error(getPlayer(), "Codex CLI error (exit " + exit + ")"));
                return;
            }

            log.info("Fielder ← [{}] in={} out={}\n{}", conversationKey, inputTokens, outputTokens, fullContent);
            onChunk.accept(PlayerResponse.done(getPlayer(), fullContent.toString(), inputTokens, outputTokens));

        } catch (Exception e) {
            log.error("Fielder error", e);
            onChunk.accept(PlayerResponse.error(getPlayer(), e.getMessage()));
        } finally {
            activeProcesses.remove(conversationId);
        }
    }
}
