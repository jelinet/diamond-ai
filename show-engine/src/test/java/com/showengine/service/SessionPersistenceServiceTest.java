package com.showengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.showengine.config.ShowEngineProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPersistenceServiceTest {

    @TempDir
    Path dataDir;

    private SessionPersistenceService service() throws Exception {
        ShowEngineProperties properties = new ShowEngineProperties();
        properties.getSessions().setDataDir(dataDir.toString());
        return new SessionPersistenceService(properties, new ObjectMapper());
    }

    @Test
    void searchSummariesReturnsRecentSessionsWhenQueryIsBlank() throws Exception {
        SessionPersistenceService service = service();
        saveSession(service, "older", "Older chat", 1, "first question");
        saveSession(service, "middle", "Middle chat", 2, "second question");
        saveSession(service, "newer", "Newer chat", 3, "third question");

        List<Map<String, Object>> summaries = service.searchSummaries("", 2);

        assertThat(summaries).extracting(summary -> summary.get("id"))
                .containsExactly("newer", "middle");
    }

    @Test
    void searchSummariesMatchesHistoricalQuestions() throws Exception {
        SessionPersistenceService service = service();
        saveSession(service, "roadmap", "Roadmap", 1, "Plan release work");
        saveSession(service, "debug", "Debugging", 2, "Find search bug");

        List<Map<String, Object>> summaries = service.searchSummaries("search bug", 10);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).get("id")).isEqualTo("debug");
        assertThat(summaries.get(0).get("lastQuestion")).isEqualTo("Find search bug");
    }

    @Test
    void searchSummariesSkipsEmptyNewChats() throws Exception {
        SessionPersistenceService service = service();
        saveEmptySession(service, "empty", "New conversation", 3);
        saveSession(service, "chat", "Real chat", 2, "hello");

        List<Map<String, Object>> summaries = service.searchSummaries("", 10);

        assertThat(summaries).extracting(summary -> summary.get("id"))
                .containsExactly("chat");
    }

    private void saveSession(SessionPersistenceService service, String id, String title, long createdAt, String question) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode session = mapper.createObjectNode();
        session.put("id", id);
        session.put("title", title);
        session.put("masterPlayer", "PITCHER");
        session.put("createdAt", createdAt);
        session.putArray("turns")
                .addObject()
                .put("id", id + "-turn")
                .put("question", question);
        service.save(id, session);
    }

    private void saveEmptySession(SessionPersistenceService service, String id, String title, long createdAt) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode session = mapper.createObjectNode();
        session.put("id", id);
        session.put("title", title);
        session.put("masterPlayer", "PITCHER");
        session.put("createdAt", createdAt);
        session.putArray("turns");
        service.save(id, session);
    }
}
