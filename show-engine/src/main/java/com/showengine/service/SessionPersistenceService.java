package com.showengine.service;

import com.showengine.config.ShowEngineProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public SessionPersistenceService(ShowEngineProperties props, ObjectMapper objectMapper) throws IOException {
        this.dataDir = Path.of(props.getSessions().getDataDir()).toAbsolutePath();
        this.objectMapper = objectMapper;
        Files.createDirectories(dataDir);
        log.info("会话存储目录：{}", dataDir);
    }

    public void save(String id, JsonNode session) throws IOException {
        objectMapper.writeValue(dataDir.resolve(id + ".json").toFile(), session);
    }

    public Optional<JsonNode> load(String id) {
        Path file = dataDir.resolve(id + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(objectMapper.readTree(file.toFile()));
        } catch (IOException e) {
            log.warn("读取 session 文件失败: {}", file, e);
            return Optional.empty();
        }
    }

    /** Reads all session summaries, sorted by createdAt descending. */
    public List<Map<String, Object>> listSummaries() {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(dataDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .forEach(file -> {
                     try {
                         JsonNode node = objectMapper.readTree(file.toFile());
                         Map<String, Object> summary = new LinkedHashMap<>();
                         summary.put("id",           node.path("id").asText(""));
                         summary.put("title",        node.path("title").asText(""));
                         summary.put("masterPlayer", node.path("masterPlayer").asText(""));
                         summary.put("createdAt",    node.path("createdAt").asLong(0));
                         result.add(summary);
                     } catch (IOException e) {
                         log.warn("跳过损坏的 session 文件: {}", file, e);
                     }
                 });
        } catch (IOException e) {
            log.error("读取 sessions 目录失败", e);
        }
        result.sort(Comparator.comparingLong(s -> -((Long) s.get("createdAt"))));
        return result;
    }

    public void delete(String id) {
        try {
            Files.deleteIfExists(dataDir.resolve(id + ".json"));
        } catch (IOException e) {
            log.warn("删除 session 文件失败: {}", id, e);
        }
    }
}
