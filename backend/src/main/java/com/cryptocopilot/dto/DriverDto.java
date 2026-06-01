package com.cryptocopilot.dto;

/** One of the top-3 SHAP drivers behind an ML prediction. */
public record DriverDto(int rank, String featureName, Double featureValue, Double shapValue) {
}
