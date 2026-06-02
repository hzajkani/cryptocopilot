package com.cryptocopilot.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies the global handler turns controller exceptions into the clean
 * {@code {error, message, status}} JSON body (Stage 7 hardening), and never leaks a stack trace.
 *
 * <p>Uses {@code standaloneSetup().setControllerAdvice(...)} — the canonical way to exercise a
 * {@code @RestControllerAdvice} directly against a throwaway controller.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void illegalArgumentBecomes400() throws Exception {
        mockMvc.perform(get("/api/_test/bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("unknown symbol FOO"));
    }

    @Test
    void noSuchElementBecomes404() throws Exception {
        mockMvc.perform(get("/api/_test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unexpectedBecomesGeneric500_withoutLeakingTheCause() throws Exception {
        mockMvc.perform(get("/api/_test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500))
                // the raw cause ("connection refused …") must NOT reach the client
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("refused"))));
    }

    /** A throwaway controller that raises the exceptions the advice is meant to translate. */
    @RestController
    static class ThrowingController {
        @GetMapping("/api/_test/bad")
        void bad() {
            throw new IllegalArgumentException("unknown symbol FOO");
        }

        @GetMapping("/api/_test/missing")
        void missing() {
            throw new NoSuchElementException("no account yet — reset first");
        }

        @GetMapping("/api/_test/boom")
        void boom() {
            throw new RuntimeException("connection refused: Ollama at host.docker.internal:11434");
        }
    }
}
