package com.showengine.service.impl;

import com.showengine.config.ShowEngineProperties;
import com.showengine.enums.PlayerEnum;
import com.showengine.model.PlayerResponse;
import com.showengine.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatcherServiceImpl implements PlayerService {

    private static final Pattern AGY_CREATED_CONVERSATION =
            Pattern.compile("Created conversation ([0-9a-fA-F-]{36})");

    private final ShowEngineProperties props;
    private final ConcurrentMap<String, String> agyConversationIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public void cancel(String conversationId) {
        String conversationKey = (conversationId != null && !conversationId.isBlank()) ? conversationId : "default";
        Process p = activeProcesses.remove(conversationKey);
        if (p != null) {
            p.destroyForcibly();
            log.debug("Catcher: cancelled process for conversation {}", conversationKey);
        }
    }

    @Override
    public PlayerEnum getPlayer() {
        return PlayerEnum.CATCHER;
    }

    @Override
    public void ask(String conversationId, String question, Consumer<PlayerResponse> onChunk) {
        ShowEngineProperties.Catcher cfg = props.getCatcher();
        askReal(conversationId, question, onChunk, cfg);
    }

    // agy outputs plain text in print mode. Token counts are not available.
    private void askReal(String conversationId, String question, Consumer<PlayerResponse> onChunk, ShowEngineProperties.Catcher cfg) {
        String conversationKey = (conversationId != null && !conversationId.isBlank()) ? conversationId : "default";
        Path logFile = null;

        List<String> command = new ArrayList<>();
        command.add(cfg.getCliPath());

        String agyConversationId = agyConversationIds.get(conversationKey);
        if (agyConversationId != null && !agyConversationId.isBlank()) {
            command.add("--conversation");
            command.add(agyConversationId);
            log.debug("Catcher: resuming agy conversation {} for {}", agyConversationId, conversationKey);
        } else {
            log.debug("Catcher: starting new agy conversation for {}", conversationKey);
        }

        command.add("-p");
        command.add(question);
        command.add("--print-timeout");
        command.add(cfg.getTimeoutSeconds() + "s");
        if (cfg.getModel() != null && !cfg.getModel().isBlank()) {
            command.add("--model");
            command.add(cfg.getModel());
        }

        try {
            logFile = Files.createTempFile("agy-catcher-", ".log");
            command.add("--log-file");
            command.add(logFile.toString());
        } catch (Exception e) {
            log.warn("Catcher: failed to create agy log file, conversation id may not be captured: {}", e.getMessage());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(System.getProperty("user.dir")));
        pb.redirectErrorStream(false);
        log.info("Catcher → [{}]\n{}", conversationKey, question);

        StringBuilder fullContent = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try {
            Process process = pb.start();
            activeProcesses.put(conversationKey, process);
            process.getOutputStream().close();

            CompletableFuture<Void> stdoutReader = CompletableFuture.runAsync(() ->
                    readStdout(process, fullContent, onChunk));
            CompletableFuture<Void> stderrReader = CompletableFuture.runAsync(() ->
                    readStderr(process, stderr));

            boolean finished = process.waitFor(cfg.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                stdoutReader.cancel(true);
                stderrReader.cancel(true);
                onChunk.accept(PlayerResponse.error(getPlayer(), "Catcher CLI timed out"));
                return;
            }

            stdoutReader.join();
            stderrReader.join();

            int exit = process.exitValue();
            if (exit != 0) {
                log.error("Catcher CLI exited with code {}: {}", exit, stderr);
                if (stderr.toString().contains("not found")) {
                    agyConversationIds.remove(conversationKey);
                }
                onChunk.accept(PlayerResponse.error(getPlayer(), "Catcher CLI error (exit " + exit + ")"));
                return;
            }

            rememberAgyConversationId(conversationKey, logFile);
            log.info("Catcher ← [{}]\n{}", conversationKey, fullContent.toString().trim());
            onChunk.accept(PlayerResponse.done(getPlayer(), fullContent.toString().trim(), 0, 0));

        } catch (Exception e) {
            log.error("Catcher error", e);
            onChunk.accept(PlayerResponse.error(getPlayer(), e.getMessage()));
        } finally {
            activeProcesses.remove(conversationKey);
            if (logFile != null) {
                try {
                    Files.deleteIfExists(logFile);
                } catch (Exception e) {
                    log.debug("Catcher: failed to delete temp agy log {}", logFile);
                }
            }
        }
    }

    private void readStdout(Process process, StringBuilder fullContent, Consumer<PlayerResponse> onChunk) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                synchronized (fullContent) {
                    fullContent.append(line).append("\n");
                }
                onChunk.accept(PlayerResponse.streaming(getPlayer(), line + "\n"));
            }
        } catch (Exception e) {
            log.debug("Catcher: failed to read stdout", e);
        }
    }

    private void readStderr(Process process, StringBuilder stderr) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stderr.append(line).append("\n");
            }
        } catch (Exception e) {
            log.debug("Catcher: failed to read stderr", e);
        }
    }

    private void rememberAgyConversationId(String conversationKey, Path logFile) {
        if (logFile == null) {
            return;
        }
        try {
            String logText = Files.readString(logFile);
            Matcher matcher = AGY_CREATED_CONVERSATION.matcher(logText);
            if (matcher.find()) {
                String agyConversationId = matcher.group(1);
                agyConversationIds.put(conversationKey, agyConversationId);
                log.debug("Catcher: mapped {} to agy conversation {}", conversationKey, agyConversationId);
            }
        } catch (Exception e) {
            log.debug("Catcher: failed to parse agy conversation id from {}", logFile, e);
        }
    }
}
