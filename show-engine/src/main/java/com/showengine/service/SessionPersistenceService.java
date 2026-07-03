package com.showengine.service;

import com.showengine.config.ShowEngineProperties;
import com.showengine.model.SessionSummary;
import com.showengine.utils.JacksonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
public class SessionPersistenceService {

    private final Path dataDir;

    public SessionPersistenceService(ShowEngineProperties props) throws IOException {
        this.dataDir = Path.of(props.getSessions().getDataDir()).toAbsolutePath();
        Files.createDirectories(dataDir);
        log.info("会话存储目录：{}", dataDir);
    }

    public void save(String id, JsonNode session) throws IOException {
        Files.writeString(dataDir.resolve(id + ".json"), JacksonUtil.toJsonStr(session));
    }

    public Optional<JsonNode> load(String id) {
        Path file = dataDir.resolve(id + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.ofNullable(JacksonUtil.toJsonNode(Files.readString(file)));
        } catch (IOException e) {
            log.warn("读取 session 文件失败: {}", file, e);
            return Optional.empty();
        }
    }

    /** Reads all session summaries, sorted by createdAt descending. */
    public List<SessionSummary> listSummaries() {
        return searchSummaries("", Integer.MAX_VALUE);
    }

    public List<SessionSummary> searchSummaries(String query, int limit) {
        List<SessionSummary> result = new ArrayList<>();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int maxResults = Math.max(0, limit);

        try (Stream<Path> files = Files.list(dataDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .forEach(file -> {
                     try {
                         JsonNode node = JacksonUtil.toJsonNode(Files.readString(file));
                         if (isEmptyNewChat(node)) return;
                         if (!matchesSession(node, normalizedQuery)) return;

                         result.add(SessionSummary.builder()
                                 .id(node.path("id").asText(""))
                                 .title(node.path("title").asText(""))
                                 .masterPlayer(node.path("masterPlayer").asText(""))
                                 .createdAt(node.path("createdAt").asLong(0))
                                 .lastQuestion(lastQuestion(node))
                                 .build());
                     } catch (IOException e) {
                         log.warn("跳过损坏的 session 文件: {}", file, e);
                     }
                 });
        } catch (IOException e) {
            log.error("读取 sessions 目录失败", e);
        }
        result.sort(Comparator.comparingLong(SessionSummary::getCreatedAt).reversed());
        return maxResults >= result.size() ? result : result.subList(0, maxResults);
    }

    private boolean matchesSession(JsonNode node, String query) {
        if (query == null || query.isBlank()) return true;
        if (node.path("title").asText("").toLowerCase(Locale.ROOT).contains(query)) return true;

        for (JsonNode turn : node.path("turns")) {
            if (turn.path("question").asText("").toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEmptyNewChat(JsonNode node) {
        String title = node.path("title").asText("");
        JsonNode turns = node.path("turns");
        boolean emptyTurns = !turns.isArray() || turns.isEmpty();
        return emptyTurns && ("New conversation".equals(title) || "New chat".equals(title));
    }

    private String lastQuestion(JsonNode node) {
        JsonNode turns = node.path("turns");
        if (!turns.isArray()) return "";

        for (int i = turns.size() - 1; i >= 0; i--) {
            String question = turns.get(i).path("question").asText("");
            if (!question.isBlank()) return question;
        }
        return "";
    }

    public void delete(String id) {
        try {
            Files.deleteIfExists(dataDir.resolve(id + ".json"));
        } catch (IOException e) {
            log.warn("删除 session 文件失败: {}", id, e);
        }
    }
}
