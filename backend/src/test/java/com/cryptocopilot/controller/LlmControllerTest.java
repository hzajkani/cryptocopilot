package com.cryptocopilot.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptocopilot.rag.LlmClient;
import com.cryptocopilot.rag.LlmProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-slice test of the provider-status endpoint the sidebar toggle reads on load. */
@WebMvcTest(LlmController.class)
class LlmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmClient llm;

    @Test
    void reportsOpenAiAvailableWhenSupported() throws Exception {
        when(llm.supports(LlmProvider.OLLAMA)).thenReturn(true);
        when(llm.supports(LlmProvider.OPENAI)).thenReturn(true);

        mockMvc.perform(get("/api/llm/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.default").value("ollama"))
                .andExpect(jsonPath("$.ollama").value(true))
                .andExpect(jsonPath("$.openai").value(true));
    }

    @Test
    void reportsOpenAiUnavailableWithoutKey() throws Exception {
        when(llm.supports(LlmProvider.OLLAMA)).thenReturn(true);
        when(llm.supports(LlmProvider.OPENAI)).thenReturn(false);

        mockMvc.perform(get("/api/llm/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openai").value(false));
    }
}
