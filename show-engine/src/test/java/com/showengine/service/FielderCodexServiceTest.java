package com.showengine.service;

import com.showengine.config.ShowEngineProperties;
import com.showengine.enums.PlayerEnum;
import com.showengine.model.PlayerResponse;
import com.showengine.service.impl.FielderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FielderService - Codex CLI integration")
class FielderCodexServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should parse Codex JSON stream content and token usage")
    void parsesCodexJsonStream() throws Exception {
        Path codex = tempDir.resolve("codex-fixture");
        Files.writeString(codex, """
                #!/bin/sh
                printf '%s\\n' '{"type":"thread.started","thread_id":"test-thread"}'
                printf '%s\\n' '{"type":"turn.started"}'
                printf '%s\\n' '{"type":"item.completed","item":{"id":"item_0","type":"agent_message","text":"2"}}'
                printf '%s\\n' '{"type":"turn.completed","usage":{"input_tokens":10728,"cached_input_tokens":10624,"output_tokens":5,"reasoning_output_tokens":0}}'
                """);
        assertThat(codex.toFile().setExecutable(true)).isTrue();

        ShowEngineProperties props = new ShowEngineProperties();
        props.getFielder().setCliPath(codex.toString());
        props.getFielder().setTimeoutSeconds(5);
        props.getFielder().setModel("");

        FielderServiceImpl service = new FielderServiceImpl(props);
        List<PlayerResponse> responses = new ArrayList<>();

        service.ask("conversation-1", "1+1", responses::add);

        assertThat(responses)
                .extracting(PlayerResponse::getStatus)
                .containsExactly(PlayerResponse.Status.STREAMING, PlayerResponse.Status.DONE);

        PlayerResponse streaming = responses.get(0);
        assertThat(streaming.getPlayer()).isEqualTo(PlayerEnum.FIELDER);
        assertThat(streaming.getContent()).isEqualTo("2");

        PlayerResponse done = responses.get(1);
        assertThat(done.getPlayer()).isEqualTo(PlayerEnum.FIELDER);
        assertThat(done.getContent()).isEqualTo("2");
        assertThat(done.getInputTokens()).isEqualTo(10728);
        assertThat(done.getOutputTokens()).isEqualTo(5);
    }

    @Test
    @DisplayName("should resume Codex session for the same conversation")
    void resumesCodexSessionForSameConversation() throws Exception {
        Path argLog = tempDir.resolve("codex-args.log");
        Path codex = tempDir.resolve("codex-fixture");
        Files.writeString(codex, """
                #!/bin/sh
                printf '%%s\\n' "$*" >> "%s"
                printf '%%s\\n' '{"type":"thread.started","thread_id":"test-thread"}'
                printf '%%s\\n' '{"type":"item.completed","item":{"id":"item_0","type":"agent_message","text":"ok"}}'
                printf '%%s\\n' '{"type":"turn.completed","usage":{"input_tokens":1,"output_tokens":1}}'
                """.formatted(argLog));
        assertThat(codex.toFile().setExecutable(true)).isTrue();

        ShowEngineProperties props = new ShowEngineProperties();
        props.getFielder().setCliPath(codex.toString());
        props.getFielder().setTimeoutSeconds(5);
        props.getFielder().setModel("");

        FielderServiceImpl service = new FielderServiceImpl(props);

        service.ask("conversation-1", "first", ignored -> {});
        service.ask("conversation-1", "second", ignored -> {});

        List<String> invocations = Files.readAllLines(argLog);
        assertThat(invocations).hasSize(2);
        assertThat(invocations.get(0)).doesNotContain("resume");
        assertThat(invocations.get(0)).doesNotContain("--ephemeral");
        assertThat(invocations.get(1)).contains("resume test-thread second");
        assertThat(invocations.get(1)).doesNotContain("--ephemeral");
    }
}
