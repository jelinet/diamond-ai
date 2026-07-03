package com.showengine.service;

import com.showengine.config.ShowEngineProperties;
import com.showengine.players.enums.PlayerStatusEnum;
import com.showengine.players.model.PlayerResponse;
import com.showengine.players.service.impl.CatcherServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CatcherGeminiIntegrationTest {

    @Autowired
    private CatcherServiceImpl catcherService;

    @Autowired
    private ShowEngineProperties properties;

    @Test
    @DisplayName("CatcherService should run the CLI fixture and return streamed output")
    void testGeminiIntegration() throws Exception {
        ShowEngineProperties.Catcher cfg = properties.getCatcher();
        String originalCliPath = cfg.getCliPath();
        String originalModel = cfg.getModel();
        Path cliFixture;

        List<PlayerResponse> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            cliFixture = Files.createTempFile("gemini-fixture-", ".sh");
            Files.writeString(cliFixture, """
                    #!/bin/sh
                    printf '%s\\n' 'Hello from fixture'
                    printf '%s\\n' '2'
                    """);
            assertThat(cliFixture.toFile().setExecutable(true)).isTrue();
            cfg.setCliPath(cliFixture.toString());
            cfg.setModel("");

            catcherService.ask("test-conv-id", "1+1 equals what? Please respond with just the numeric answer.", chunk -> {
                responses.add(chunk);
                if (chunk.getStatus() == PlayerStatusEnum.DONE || chunk.getStatus() == PlayerStatusEnum.ERROR) {
                    latch.countDown();
                }
            });

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // Find the DONE event
            PlayerResponse doneResponse = responses.stream()
                    .filter(r -> r.getStatus() == PlayerStatusEnum.DONE)
                    .findFirst()
                    .orElse(null);

            assertThat(doneResponse).isNotNull();
            assertThat(doneResponse.getContent()).contains("2");
            assertThat(doneResponse.getInputTokens()).isEqualTo(0);
            assertThat(doneResponse.getOutputTokens()).isEqualTo(0);

        } finally {
            cfg.setCliPath(originalCliPath);
            cfg.setModel(originalModel);
        }
    }
}
