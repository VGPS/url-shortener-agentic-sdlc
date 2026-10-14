package com.wgblackmon.urlshortener.infrastructure.persistence;

import com.wgblackmon.urlshortener.domain.port.outbound.CodeGeneratorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates collision-resistant alphanumeric short codes using {@link SecureRandom}.
 *
 * <p>The code alphabet is base-62 (a-z, A-Z, 0-9). Default length is 6 characters,
 * yielding 62^6 ≈ 56 billion possible codes before collision probability becomes
 * material. Length is configurable via {@code urlshortener.code-length}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@Component
public class AlphanumericCodeGenerator implements CodeGeneratorPort {

    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int codeLength;

    public AlphanumericCodeGenerator(@Value("${urlshortener.code-length:6}") int codeLength) {
        log.debug("AlphanumericCodeGenerator() | codeLength={}", codeLength);
        this.codeLength = codeLength;
    }

    @Override
    public String generate() {
        log.debug("generate() | codeLength={}", codeLength);
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        String result = sb.toString();
        log.debug("generate() | return={}", result);
        return result;
    }
}
