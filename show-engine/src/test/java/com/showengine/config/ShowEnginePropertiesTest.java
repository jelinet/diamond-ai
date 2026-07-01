package com.showengine.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ShowEngineProperties - yml binding")
class ShowEnginePropertiesTest {

    @Autowired
    private ShowEngineProperties properties;

    @Test
    @DisplayName("Pitcher config should bind from application-test.yml")
    void pitcherConfigBinds() {
        assertThat(properties.getPitcher().getCliPath()).isEqualTo("claude");
        assertThat(properties.getPitcher().getTimeoutSeconds()).isEqualTo(10);
        assertThat(properties.getPitcher().getModel()).isEqualTo("claude-haiku-4-5");
    }

    @Test
    @DisplayName("Catcher config should bind from application-test.yml")
    void catcherConfigBinds() {
        assertThat(properties.getCatcher().getCliPath()).isEqualTo("gemini");
        assertThat(properties.getCatcher().getTimeoutSeconds()).isEqualTo(10);
        assertThat(properties.getCatcher().getModel()).isEqualTo("gemini-3.5-flash");
    }

    @Test
    @DisplayName("Fielder config should bind from application-test.yml")
    void fielderConfigBinds() {
        assertThat(properties.getFielder().getCliPath()).isEqualTo("codex");
        assertThat(properties.getFielder().getTimeoutSeconds()).isEqualTo(10);
        assertThat(properties.getFielder().getModel()).isEmpty();
    }
}
