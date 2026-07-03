package com.showengine.players.service.impl;

import com.showengine.config.ShowEngineProperties;
import com.showengine.enums.PlayerEnum;
import com.showengine.players.model.PlayerResponse;
import com.showengine.players.service.PlayerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.showengine.utils.JacksonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class PitcherServiceImpl implements PlayerService {

    private final ShowEngineProperties props;
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public void cancel(String conversationId) {
        Process p = activeProcesses.remove(conversationId);
        if (p != null) {
            p.destroyForcibly();
            log.debug("Pitcher: cancelled process for conversation {}", conversationId);
        }
    }

    @Override
    public PlayerEnum getPlayer() {
        return PlayerEnum.PITCHER;
    }

    @Override
    public void ask(String conversationId, String question, Consumer<PlayerResponse> onChunk) {
        ShowEngineProperties.Pitcher cfg = props.getPitcher();

        ProcessBuilder pb;
        if (activeSessions.contains(conversationId)) {
            pb = new ProcessBuilder(
                    cfg.getCliPath(),
                    "-p", question,
                    "--resume", conversationId,
                    "--output-format", "stream-json",
                    "--verbose",
                    "--allowedTools", "",
                    "--model", cfg.getModel()
            );
            log.debug("Pitcher: resuming session {}", conversationId);
        } else {
            pb = new ProcessBuilder(
                    cfg.getCliPath(),
                    "-p", question,
                    "--session-id", conversationId,
                    "--output-format", "stream-json",
                    "--verbose",
                    "--allowedTools", "",
                    "--model", cfg.getModel()
            );
            activeSessions.add(conversationId);
            log.debug("Pitcher: starting new session {}", conversationId);
        }
        pb.redirectErrorStream(false);
        log.info("Pitcher → [{}]\n{}", conversationId, question);

        int inputTokens = 0;
        int outputTokens = 0;
        StringBuilder fullContent = new StringBuilder();

        try {
            Process process = pb.start();
            activeProcesses.put(conversationId, process);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        JsonNode node = JacksonUtil.toJsonNode(line);
                        String type = node.path("type").asText();

                        switch (type) {
                            case "assistant" -> {
                                // Claude Code CLI format: message.content[].text
                                JsonNode contentArr = node.path("message").path("content");
                                for (JsonNode item : contentArr) {
                                    if ("text".equals(item.path("type").asText())) {
                                        String text = item.path("text").asText("");
                                        if (!text.isEmpty()) {
                                            fullContent.append(text);
                                            onChunk.accept(PlayerResponse.streaming(getPlayer(), text));
                                        }
                                    }
                                }
                            }
                            case "result" -> {
                                // Final event with aggregated token usage
                                JsonNode usage = node.path("usage");
                                inputTokens = usage.path("input_tokens").asInt();
                                outputTokens = usage.path("output_tokens").asInt();
                            }
                            default -> log.debug("Pitcher: ignoring event type={}", type);
                        }
                    } catch (Exception e) {
                        log.debug("Pitcher: failed to parse line: {}", line);
                    }
                }
            }

            boolean finished = process.waitFor(cfg.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                onChunk.accept(PlayerResponse.error(getPlayer(), "Claude CLI timed out"));
                return;
            }

            int exit = process.exitValue();
            if (exit != 0) {
                String stderr = new String(process.getErrorStream().readAllBytes());
                log.error("Pitcher CLI exited with code {}: {}", exit, stderr);
                onChunk.accept(PlayerResponse.error(getPlayer(), "Claude CLI error (exit " + exit + ")"));
                return;
            }

            log.info("Pitcher ← [{}] in={} out={}\n{}", conversationId, inputTokens, outputTokens, fullContent);
            onChunk.accept(PlayerResponse.done(getPlayer(), fullContent.toString(), inputTokens, outputTokens));

        } catch (Exception e) {
            log.error("Pitcher error", e);
            onChunk.accept(PlayerResponse.error(getPlayer(), e.getMessage()));
        } finally {
            activeProcesses.remove(conversationId);
        }
    }
}
