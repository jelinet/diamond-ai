package com.showengine.service;

import com.showengine.config.ShowEngineProperties;
import com.showengine.enums.PlayerEnum;
import com.showengine.players.enums.PlayerStatusEnum;
import com.showengine.players.model.PlayerResponse;
import com.showengine.players.service.impl.CatcherServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CatcherService - agy CLI integration")
class CatcherGeminiServiceTest {

    private static final String AGY_CONVERSATION_ID = "11111111-1111-1111-1111-111111111111";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should parse agy plain text content")
    void parsesAgyPlainText() throws Exception {
        Path agy = writeAgyFixture(tempDir.resolve("agy-args.log"));

        CatcherServiceImpl service = new CatcherServiceImpl(propsFor(agy));
        List<PlayerResponse> responses = new ArrayList<>();

        service.ask("frontend-conversation", "1+1", responses::add);

        assertThat(responses)
                .extracting(PlayerResponse::getStatus)
                .containsExactly(PlayerStatusEnum.STREAMING, PlayerStatusEnum.DONE);

        assertThat(responses.get(0).getPlayer()).isEqualTo(PlayerEnum.CATCHER);
        assertThat(responses.get(0).getContent()).isEqualTo("2\n");

        PlayerResponse done = responses.get(1);
        assertThat(done.getPlayer()).isEqualTo(PlayerEnum.CATCHER);
        assertThat(done.getContent()).isEqualTo("2");
        assertThat(done.getInputTokens()).isEqualTo(0);
        assertThat(done.getOutputTokens()).isEqualTo(0);
    }

    @Test
    @DisplayName("should resume with agy conversation id instead of frontend conversation id")
    void resumesWithAgyConversationId() throws Exception {
        Path argLog = tempDir.resolve("agy-args.log");
        Path agy = writeAgyFixture(argLog);

        CatcherServiceImpl service = new CatcherServiceImpl(propsFor(agy));

        service.ask("frontend-conversation", "first", ignored -> {});
        service.ask("frontend-conversation", "second", ignored -> {});

        List<String> invocations = Files.readAllLines(argLog);
        assertThat(invocations).hasSize(2);
        assertThat(invocations.get(0)).doesNotContain("--conversation");
        assertThat(invocations.get(0)).doesNotContain("frontend-conversation");
        assertThat(invocations.get(1)).contains("--conversation " + AGY_CONVERSATION_ID);
        assertThat(invocations.get(1)).doesNotContain("--conversation frontend-conversation");
    }

    private Path writeAgyFixture(Path argLog) throws Exception {
        Path fixture = tempDir.resolve("agy-fixture");
        Files.writeString(fixture, """
                #!/bin/sh
                log_file=""
                original_args="$*"
                while [ "$#" -gt 0 ]; do
                  case "$1" in
                    --log-file)
                      shift
                      log_file="$1"
                      ;;
                  esac
                  shift
                done
                printf '%%s\\n' "$original_args" >> "%s"
                if [ -n "$log_file" ]; then
                  printf '%%s\\n' 'I0629 00:00:00.000000 server.go:799] Created conversation %s' > "$log_file"
                fi
                printf '%%s\\n' '2'
                """.formatted(argLog, AGY_CONVERSATION_ID));
        assertThat(fixture.toFile().setExecutable(true)).isTrue();
        return fixture;
    }

    private ShowEngineProperties propsFor(Path cliPath) {
        ShowEngineProperties props = new ShowEngineProperties();
        props.getCatcher().setCliPath(cliPath.toString());
        props.getCatcher().setTimeoutSeconds(5);
        props.getCatcher().setModel("");
        return props;
    }
}
