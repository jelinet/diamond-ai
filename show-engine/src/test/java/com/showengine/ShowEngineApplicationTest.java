package com.showengine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ShowEngineApplicationTest {

    @Test
    void contextLoads() {
        // Verifies the Spring application context starts without errors.
        // If any bean configuration is broken, this test will fail.
    }
}
