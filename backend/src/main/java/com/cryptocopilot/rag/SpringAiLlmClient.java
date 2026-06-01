package com.cryptocopilot.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * {@link LlmClient} backed by Spring AI's {@link ChatClient} over whichever chat model is
 * auto-configured. Stage 4 uses a local <b>Ollama</b> model ({@code llama3.2}, temperature 0 —
 * see {@code application.yml} and {@code docs/OLLAMA_SETUP.md}); this class is provider-agnostic,
 * so switching the active chat provider needs no change here.
 */
@Component
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;

    public SpringAiLlmClient(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        return content == null ? "" : content;
    }
}
