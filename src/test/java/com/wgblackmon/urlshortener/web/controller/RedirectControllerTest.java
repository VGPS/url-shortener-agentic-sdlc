package com.wgblackmon.urlshortener.web.controller;

import com.wgblackmon.urlshortener.domain.model.ShortUrl;
import com.wgblackmon.urlshortener.domain.port.inbound.ResolveUrlUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.TrackClickUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link RedirectController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@WebMvcTest(RedirectController.class)
@Import(GlobalExceptionHandler.class)
class RedirectControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ResolveUrlUseCase resolveUrlUseCase;
    @MockBean TrackClickUseCase trackClickUseCase;

    @Test
    void redirect_existingCode_returns302ToOriginalUrl() throws Exception {
        ShortUrl shortUrl = new ShortUrl("abc123", "https://example.com",
                Instant.now(), Instant.now().plusSeconds(86400), null, true);
        when(resolveUrlUseCase.resolve("abc123")).thenReturn(Optional.of(shortUrl));
        when(trackClickUseCase.track(any(), any(), any(), any())).thenReturn(null);

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        when(resolveUrlUseCase.resolve("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound());
    }
}
