package com.showengine.service;

import com.showengine.config.ShowEngineProperties;
import com.showengine.model.SessionSummary;
import com.showengine.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPersistenceServiceTest {

    @TempDir
    Path dataDir;

    private SessionPersistenceService service() throws Exception {
        ShowEngineProperties properties = new ShowEngineProperties();
        properties.getSessions().setDataDir(dataDir.toString());
        return new SessionPersistenceService(properties);
    }

    @Test
    void searchSummariesReturnsRecentSessionsWhenQueryIsBlank() throws Exception {
        SessionPersistenceService service = service();
        saveSession(service, "older", "Older chat", 1, "first question");
        saveSession(service, "middle", "Middle chat", 2, "second question");
        saveSession(service, "newer", "Newer chat", 3, "third question");

        List<SessionSummary> summaries = service.searchSummaries("", 2);

        assertThat(summaries).extracting(SessionSummary::getId)
                .containsExactly("newer", "middle");
    }

    @Test
    void searchSummariesMatchesHistoricalQuestions() throws Exception {
        SessionPersistenceService service = service();
        saveSession(service, "roadmap", "Roadmap", 1, "Plan release work");
        saveSession(service, "debug", "Debugging", 2, "Find search bug");

        List<SessionSummary> summaries = service.searchSummaries("search bug", 10);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getId()).isEqualTo("debug");
        assertThat(summaries.get(0).getLastQuestion()).isEqualTo("Find search bug");
    }

    @Test
    void searchSummariesSkipsEmptyNewChats() throws Exception {
        SessionPersistenceService service = service();
        saveEmptySession(service, "empty", "New conversation", 3);
        saveSession(service, "chat", "Real chat", 2, "hello");

        List<SessionSummary> summaries = service.searchSummaries("", 10);

        assertThat(summaries).extracting(SessionSummary::getId)
                .containsExactly("chat");
    }

    private void saveSession(SessionPersistenceService service, String id, String title, long createdAt, String question) throws Exception {
        service.save(id, JacksonUtil.toJsonNode("""
                {
                  "id": "%s",
                  "title": "%s",
                  "masterPlayer": "PITCHER",
                  "createdAt": %d,
                  "turns": [{"id": "%s-turn", "question": "%s"}]
                }
                """.formatted(id, title, createdAt, id, question)));
    }

    private void saveEmptySession(SessionPersistenceService service, String id, String title, long createdAt) throws Exception {
        service.save(id, JacksonUtil.toJsonNode("""
                {
                  "id": "%s",
                  "title": "%s",
                  "masterPlayer": "PITCHER",
                  "createdAt": %d,
                  "turns": []
                }
                """.formatted(id, title, createdAt)));
    }
}
