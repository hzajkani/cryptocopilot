package com.cryptocopilot.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the two chat clients the {@link SpringAiLlmClient} routes between.
 *
 * <ul>
 *   <li><b>Ollama</b> ({@code ollamaChatClient}) wraps the single auto-configured {@link ChatModel}.
 *       {@code spring.ai.model.chat=ollama} (see {@code application.yml}) makes that model Ollama,
 *       so this is always present and is the default/free provider.</li>
 *   <li><b>OpenAI</b> ({@code openAiChatClient}) is built by hand from {@code OPENAI_API_KEY} and is
 *       only registered when a real key is configured (it must start with {@code sk-}). It is a
 *       plain {@link ChatClient} bean — <em>not</em> a second {@link ChatModel} bean — so it never
 *       makes {@code ChatModel} injection ambiguous, and the OpenAI <em>embedding</em> model is
 *       never created (embeddings stay on Ollama, 768-dim — no reindex on toggle).</li>
 * </ul>
 *
 * Construction is offline (no network, no key validation), so a missing/invalid key never breaks
 * boot — at worst the OpenAI client is simply absent and the toggle stays on Ollama.
 */
@Configuration
public class LlmConfig {

    /** The default, free provider: the auto-configured Ollama chat model. */
    @Bean
    ChatClient ollamaChatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    /**
     * OpenAI {@code gpt-4o-mini}, registered only when {@code OPENAI_API_KEY} looks real
     * (starts with {@code sk-}). Absent otherwise, which leaves the UI toggle's OpenAI side disabled.
     */
    @Bean
    @ConditionalOnExpression("'${spring.ai.openai.api-key:not-used}'.startsWith('sk-')")
    ChatClient openAiChatClient(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${spring.ai.openai.chat.options.temperature:0.0}") Double temperature) {
        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();
        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
        return ChatClient.create(openAiChatModel);
    }
}
