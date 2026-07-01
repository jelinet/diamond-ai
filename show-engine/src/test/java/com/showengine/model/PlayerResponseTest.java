package com.showengine.model;

import com.showengine.enums.PlayerEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlayerResponse - factory methods")
class PlayerResponseTest {

    @Test
    @DisplayName("streaming() creates correct STREAMING event")
    void streamingFactory() {
        PlayerResponse r = PlayerResponse.streaming(PlayerEnum.PITCHER, "Hello");

        assertThat(r.getPlayer()).isEqualTo(PlayerEnum.PITCHER);
        assertThat(r.getStatus()).isEqualTo(PlayerResponse.Status.STREAMING);
        assertThat(r.getContent()).isEqualTo("Hello");
        assertThat(r.getInputTokens()).isNull();
        assertThat(r.getOutputTokens()).isNull();
        assertThat(r.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("done() creates correct DONE event with token counts")
    void doneFactory() {
        PlayerResponse r = PlayerResponse.done(PlayerEnum.CATCHER, "Full answer", 42, 100);

        assertThat(r.getPlayer()).isEqualTo(PlayerEnum.CATCHER);
        assertThat(r.getStatus()).isEqualTo(PlayerResponse.Status.DONE);
        assertThat(r.getContent()).isEqualTo("Full answer");
        assertThat(r.getInputTokens()).isEqualTo(42);
        assertThat(r.getOutputTokens()).isEqualTo(100);
    }

    @Test
    @DisplayName("error() creates correct ERROR event")
    void errorFactory() {
        PlayerResponse r = PlayerResponse.error(PlayerEnum.FIELDER, "CLI not found");

        assertThat(r.getPlayer()).isEqualTo(PlayerEnum.FIELDER);
        assertThat(r.getStatus()).isEqualTo(PlayerResponse.Status.ERROR);
        assertThat(r.getErrorMessage()).isEqualTo("CLI not found");
        assertThat(r.getContent()).isNull();
    }
}
