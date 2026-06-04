package com.cryptocopilot.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thin HTTP client over the Python ML service's FastAPI ({@link MlProperties#baseUrl}). It mirrors
 * the five endpoints; the JSON shapes are forwarded verbatim as a {@link JsonNode} so the Python
 * side stays the single source of truth for the payloads (no DTO duplication on the Java side).
 *
 * <p>Error mapping: an HTTP error from the ML service (e.g. 409 "a job is already running") is
 * re-thrown with the same status and its {@code detail} message; a transport failure (the service
 * is down) becomes a clean {@code 502 Bad Gateway}. Both surface through the existing
 * {@code GlobalExceptionHandler} as a tidy {@code ApiError}.
 */
@Component
public class MlClient {

    private static final Logger log = LoggerFactory.getLogger(MlClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final RestClient http;

    public MlClient(MlProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new ResponseStatusException(response.getStatusCode(), detailOf(body));
                })
                .build();
    }

    /** Pipeline snapshot: row counts, model card, latest predictions, active job. */
    public JsonNode status() {
        return call(() -> http.get().uri("/status").retrieve().body(JsonNode.class));
    }

    /** Start an ingestion run; returns the job to poll. */
    public JsonNode ingest() {
        return call(() -> http.post().uri("/ingest").retrieve().body(JsonNode.class));
    }

    /** Start a training run; returns the job to poll. */
    public JsonNode train() {
        return call(() -> http.post().uri("/train").retrieve().body(JsonNode.class));
    }

    /** Run a prediction pass; returns the job to poll. */
    public JsonNode predict() {
        return call(() -> http.post().uri("/predict").retrieve().body(JsonNode.class));
    }

    /** Poll one job by id. */
    public JsonNode job(String id) {
        return call(() -> http.get().uri("/jobs/{id}", id).retrieve().body(JsonNode.class));
    }

    /** Run a call, translating a transport failure into a clean 502 (the service is down). */
    private JsonNode call(Supplier<JsonNode> op) {
        try {
            return op.get();
        } catch (ResponseStatusException ex) {
            throw ex; // a deliberate status from the ML service (e.g. 409) — preserve it
        } catch (Exception ex) {
            log.warn("ML service call failed: {}", ex.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The ML service is unreachable. Start it with `docker compose up -d ml-api` "
                            + "(or run `uvicorn ml.api:app` in ml/).");
        }
    }

    /** Pull FastAPI's {@code {"detail": ...}} message out of an error body, else the raw text. */
    private static String detailOf(String body) {
        if (body == null || body.isBlank()) {
            return "ML service error";
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node.hasNonNull("detail")) {
                return node.get("detail").asText();
            }
        } catch (Exception ignore) {
            // not JSON — fall through to the raw body
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }
}
