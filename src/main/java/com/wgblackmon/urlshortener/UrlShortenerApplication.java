package com.wgblackmon.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the URL Shortener + Agentic SDLC Orchestration System.
 *
 * <p>This application demonstrates an agentic orchestration layer coordinating
 * the full SDLC lifecycle (Requirements → Design → Implementation → Testing →
 * Documentation → Release Readiness) using Claude as the AI execution engine.
 * The URL shortener is the domain the orchestrator operates on.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
