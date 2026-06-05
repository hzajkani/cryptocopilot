package com.cryptocopilot.controller;

import com.cryptocopilot.rag.AnswerWithCitations;
import com.cryptocopilot.rag.ChatRequest;
import com.cryptocopilot.rag.LlmProvider;
import com.cryptocopilot.rag.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Researcher: strictly-grounded, cited RAG chat over news + on-chain + fundamentals + the
 * curated Knowledge Base (PROJECT.md Stage 4). Decision-support, not financial advice.
 */
@RestController
@Tag(name = "Researcher (RAG)", description = "Grounded, cited chat over the corpus; index admin")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @Operation(summary = "Ask the Researcher",
            description = "Strictly-grounded answer with [N] citations. Refuses out-of-corpus "
                    + "questions and trading advice with fixed phrases; never hallucinates. "
                    + "Optional body field 'provider' (\"ollama\" default / \"openai\") selects the "
                    + "answering model; the response echoes the provider actually used.")
    @PostMapping("/api/chat")
    public AnswerWithCitations chat(@RequestBody ChatRequest request) {
        return ragService.chat(request.query(), request.symbols(),
                LlmProvider.fromLabel(request.provider()));
    }

    @Operation(summary = "RAG index status",
            description = "Chunk counts currently in the pgvector store, per source type.")
    @GetMapping("/api/rag/status")
    public Map<String, Integer> status() {
        return ragService.status();
    }

    @Operation(summary = "Rebuild the RAG index",
            description = "Clear-and-rebuild the corpus from news + on-chain + fundamentals + KB. "
                    + "Idempotent; returns the new chunk counts per source type.")
    @PostMapping("/api/rag/reindex")
    public Map<String, Integer> reindex() {
        return ragService.reindex();
    }
}
