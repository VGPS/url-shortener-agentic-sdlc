package com.wgblackmon.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Application context smoke test.
 *
 * <p>Verifies the Spring context loads without errors. The Anthropic API key
 * is set to a dummy value so no real AI calls are made during this test.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.anthropic.api-key=test-key-not-real"
})
class UrlShortenerApplicationTest {

    @Test
    void contextLoads() {
        // passes if Spring context starts without errors
    }
}
