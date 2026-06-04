package com.cryptocopilot.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the Python ML/data service's HTTP API (FastAPI, {@code ml/ml/api.py}).
 * Bound from {@code cryptocopilot.ml.*}. The base URL is {@code http://ml-api:8000} on the compose
 * network (set via {@code ML_API_URL}) and {@code http://localhost:8000} for local dev.
 *
 * @param baseUrl root URL of the ML service, without a trailing slash
 */
@ConfigurationProperties(prefix = "cryptocopilot.ml")
public record MlProperties(String baseUrl) {

    public MlProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8000";
        }
    }
}
