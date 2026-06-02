package com.cryptocopilot.web;

/**
 * The clean JSON error body returned by {@link GlobalExceptionHandler} instead of a stack trace.
 *
 * @param error   the HTTP reason phrase (e.g. {@code "Bad Request"})
 * @param message a human-readable detail safe to show a client (never a stack trace)
 * @param status  the numeric HTTP status code
 */
public record ApiError(String error, String message, int status) {}
