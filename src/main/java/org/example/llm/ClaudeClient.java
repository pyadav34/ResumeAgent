package org.example.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import org.example.config.AppConfig;

public class ClaudeClient implements LlmClient {

    private final AnthropicClient client;

    public ClaudeClient() {
        this.client = AnthropicOkHttpClient.fromEnv();
    }

    @Override
    public String call(String systemPrompt, String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(AppConfig.ANTHROPIC_MODEL))
                .maxTokens(8192L)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                .build();
        Message response = client.messages().create(params);
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElse("");
    }
}
