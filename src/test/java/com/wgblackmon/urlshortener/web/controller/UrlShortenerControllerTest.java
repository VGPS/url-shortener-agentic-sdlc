package com.wgblackmon.urlshortener.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.urlshortener.domain.exception.CodeAlreadyExistsException;
import com.wgblackmon.urlshortener.domain.exception.InvalidUrlException;
import com.wgblackmon.urlshortener.domain.exception.UrlNotFoundException;
import com.wgblackmon.urlshortener.domain.model.AnalyticsSummary;
import com.wgblackmon.urlshortener.domain.model.ShortUrl;
import com.wgblackmon.urlshortener.domain.model.ShortenResult;
import com.wgblackmon.urlshortener.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.ResolveUrlUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.ShortenUrlUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link UrlShortenerController}.
 *
 * <p>All use cases are mocked — no Spring context, database, or AI calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@WebMvcTest(UrlShortenerController.class)
@Import(GlobalExceptionHandler.class)
class UrlShortenerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ShortenUrlUseCase  shortenUrlUseCase;
    @MockBean ResolveUrlUseCase  resolveUrlUseCase;
    @MockBean GetAnalyticsUseCase getAnalyticsUseCase;

    @Test
    void shorten_validRequest_returns201WithShortCode() throws Exception {
        ShortUrl shortUrl = new ShortUrl("abc123", "https://example.com",
                Instant.now(), Instant.now().plusSeconds(86400), null, true);
        when(shortenUrlUseCase.shorten(any()))
                .thenReturn(new ShortenResult(shortUrl, "abc123", "http://localhost/abc123", false));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.alreadyExisted").value(false))
                .andExpect(header().string("Location", "/api/v1/urls/abc123"));
    }

    @Test
    void shorten_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shorten_invalidUrl_returns400() throws Exception {
        when(shortenUrlUseCase.shorten(any())).thenThrow(new InvalidUrlException("ftp://bad.com"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"ftp://bad.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void shorten_customCodeConflict_returns409() throws Exception {
        when(shortenUrlUseCase.shorten(any())).thenThrow(new CodeAlreadyExistsException("mycode"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customCode\":\"mycode\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void getDetails_existingCode_returns200() throws Exception {
        ShortUrl shortUrl = new ShortUrl("abc123", "https://example.com",
                Instant.now(), Instant.now().plusSeconds(86400), null, true);
        when(resolveUrlUseCase.resolve("abc123")).thenReturn(Optional.of(shortUrl));

        mockMvc.perform(get("/api/v1/urls/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    void getDetails_unknownCode_returns404() throws Exception {
        when(resolveUrlUseCase.resolve("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/urls/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void deactivate_existingCode_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/abc123"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivate_unknownCode_returns404() throws Exception {
        doThrow(new UrlNotFoundException("nope")).when(shortenUrlUseCase).deactivate("nope");

        mockMvc.perform(delete("/api/v1/urls/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAnalytics_existingCode_returns200WithStats() throws Exception {
        AnalyticsSummary summary = new AnalyticsSummary(
                "abc123", 42L, Map.of(), List.of("https://ref.com"), Instant.now()
        );
        when(getAnalyticsUseCase.getSummary("abc123")).thenReturn(summary);

        mockMvc.perform(get("/api/v1/urls/abc123/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(42))
                .andExpect(jsonPath("$.topReferrers[0]").value("https://ref.com"));
    }

    @Test
    void getAnalytics_unknownCode_returns404() throws Exception {
        when(getAnalyticsUseCase.getSummary("nope")).thenThrow(new UrlNotFoundException("nope"));

        mockMvc.perform(get("/api/v1/urls/nope/analytics"))
                .andExpect(status().isNotFound());
    }
}
