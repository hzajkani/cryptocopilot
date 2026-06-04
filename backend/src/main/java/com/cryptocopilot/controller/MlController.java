package com.cryptocopilot.controller;

import com.cryptocopilot.ml.MlClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy to the Python ML/data service: trigger ingest / train / predict and read pipeline status.
 * These forward straight to the FastAPI in {@code ml/ml/api.py} via {@link MlClient}; ingest and
 * train are long-running, so they return a <em>job</em> that the caller polls at
 * {@code /api/ml/jobs/{id}}. Decision-support only — not financial advice.
 */
@RestController
@RequestMapping("/api/ml")
@Tag(name = "ML pipeline",
        description = "Trigger ingest/train/predict on the Python ML service and read its status")
public class MlController {

    private final MlClient ml;

    public MlController(MlClient ml) {
        this.ml = ml;
    }

    @Operation(summary = "ML pipeline status",
            description = "Row counts per data table, the trained-model card summary, the latest "
                    + "predictions, and any job currently running.")
    @GetMapping("/status")
    public JsonNode status() {
        return ml.status();
    }

    @Operation(summary = "Start a data-ingestion run",
            description = "Crawls the five public sources into Postgres (~minutes). Returns a job; "
                    + "poll /api/ml/jobs/{id} for completion + per-source counts.")
    @PostMapping("/ingest")
    public JsonNode ingest() {
        return ml.ingest();
    }

    @Operation(summary = "Start model training",
            description = "Builds features, tunes + fits the calibrated XGBoost, writes models/v1/ "
                    + "(~minutes). Returns a job; poll /api/ml/jobs/{id} for the test metrics.")
    @PostMapping("/train")
    public JsonNode train() {
        return ml.train();
    }

    @Operation(summary = "Run prediction",
            description = "Writes the latest direction forecast + top-3 SHAP drivers for all 10 "
                    + "coins. Returns a job; poll /api/ml/jobs/{id} for the written predictions.")
    @PostMapping("/predict")
    public JsonNode predict() {
        return ml.predict();
    }

    @Operation(summary = "Poll a pipeline job",
            description = "State (running/success/error) + result payload for a job started by "
                    + "ingest/train/predict.")
    @GetMapping("/jobs/{id}")
    public JsonNode job(
            @Parameter(description = "Job id returned by ingest/train/predict") @PathVariable String id) {
        return ml.job(id);
    }
}
