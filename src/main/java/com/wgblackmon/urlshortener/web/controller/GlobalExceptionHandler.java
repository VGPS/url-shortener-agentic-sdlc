package com.wgblackmon.urlshortener.web.controller;

import com.wgblackmon.urlshortener.domain.exception.ApprovalNotFoundException;
import com.wgblackmon.urlshortener.domain.exception.CodeAlreadyExistsException;
import com.wgblackmon.urlshortener.domain.exception.InvalidUrlException;
import com.wgblackmon.urlshortener.domain.exception.OrchestrationRunNotFoundException;
import com.wgblackmon.urlshortener.domain.exception.UrlNotFoundException;
import com.wgblackmon.urlshortener.web.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised exception handler mapping domain exceptions to HTTP status codes.
 *
 * <p>All handler methods log at WARN level and return a consistent
 * {@link ErrorResponse} body so API clients can parse errors uniformly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UrlNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUrlNotFound(UrlNotFoundException ex) {
        log.warn("handleUrlNotFound() | message={}", ex.getMessage());
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(CodeAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleCodeAlreadyExists(CodeAlreadyExistsException ex) {
        log.warn("handleCodeAlreadyExists() | message={}", ex.getMessage());
        return new ErrorResponse("CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(InvalidUrlException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidUrl(InvalidUrlException ex) {
        log.warn("handleInvalidUrl() | message={}", ex.getMessage());
        return new ErrorResponse("BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(OrchestrationRunNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrchestrationRunNotFound(OrchestrationRunNotFoundException ex) {
        log.warn("handleOrchestrationRunNotFound() | message={}", ex.getMessage());
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ApprovalNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleApprovalNotFound(ApprovalNotFoundException ex) {
        log.warn("handleApprovalNotFound() | message={}", ex.getMessage());
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalState(IllegalStateException ex) {
        log.warn("handleIllegalState() | message={}", ex.getMessage());
        return new ErrorResponse("CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("handleIllegalArgument() | message={}", ex.getMessage());
        return new ErrorResponse("BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("Validation failed");
        log.warn("handleValidation() | message={}", message);
        return new ErrorResponse("BAD_REQUEST", message);
    }
}
