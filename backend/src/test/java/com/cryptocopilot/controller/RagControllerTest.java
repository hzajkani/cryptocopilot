package com.cryptocopilot.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptocopilot.rag.AnswerWithCitations;
import com.cryptocopilot.rag.Citation;
import com.cryptocopilot.rag.RagService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-slice test for the RAG endpoints with a mocked {@link RagService}. */
@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @Test
    void chatReturnsAnswerWithCitations() throws Exception {
        Citation cite = new Citation(1, "kb", "SOL", "kb", null,
                Instant.parse("2026-05-30T00:00:00Z"), "Solana uses Proof of History…");
        AnswerWithCitations answer = new AnswerWithCitations(
                "Solana combines Proof of History with Proof of Stake [1].",
                List.of(cite), List.of(), 142L, "kb");
        when(ragService.chat(eq("How does Solana achieve consensus?"), any())).thenReturn(answer);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"How does Solana achieve consensus?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(
                        "Solana combines Proof of History with Proof of Stake [1]."))
                .andExpect(jsonPath("$.citations[0].number").value(1))
                .andExpect(jsonPath("$.citations[0].symbol").value("SOL"))
                .andExpect(jsonPath("$.queryClassification").value("kb"));
    }

    @Test
    void statusReturnsCountsPerSourceType() throws Exception {
        when(ragService.status()).thenReturn(Map.of(
                "news", 124, "onchain", 52, "fundamental", 10, "kb", 70));

        mockMvc.perform(get("/api/rag/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kb").value(70))
                .andExpect(jsonPath("$.news").value(124));
    }

    @Test
    void reindexReturnsNewCounts() throws Exception {
        when(ragService.reindex()).thenReturn(Map.of(
                "news", 124, "onchain", 52, "fundamental", 10, "kb", 70));

        mockMvc.perform(post("/api/rag/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fundamental").value(10))
                .andExpect(jsonPath("$.onchain").value(52));
    }
}
