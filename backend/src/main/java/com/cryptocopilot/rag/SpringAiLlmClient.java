package com.cryptocopilot.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * {@link LlmClient} backed by Spring AI's {@link ChatClient}, routing each call to the requested
 * {@link LlmProvider}. Ollama ({@code llama3.2:3b}, the free default) is always available; OpenAI
 * ({@code gpt-4o-mini}) is available only when {@code OPENAI_API_KEY} is configured — see
 * {@link LlmConfig}. A request for OpenAI when it is unavailable transparently falls back to Ollama
 * (and {@link Generator}/{@code AnalystSummarizer} report the provider actually used).
 */
@Component
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient ollama;
    private final ChatClient openAi;   // null when no OPENAI_API_KEY is configured

    public SpringAiLlmClient(
            @Qualifier("ollamaChatClient") ChatClient ollama,
            @Qualifier("openAiChatClient") ObjectProvider<ChatClient> openAi) {
        this.ollama = ollama;
        this.openAi = openAi.getIfAvailable();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, LlmProvider.OLLAMA);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, LlmProvider provider) {
        ChatClient client = (provider == LlmProvider.OPENAI && openAi != null) ? openAi : ollama;
        String content = client.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        return content == null ? "" : content;
    }

    @Override
    public boolean supports(LlmProvider provider) {
        return provider != LlmProvider.OPENAI || openAi != null;
    }
}
