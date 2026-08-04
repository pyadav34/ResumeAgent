package org.example.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlockParam;
import org.example.config.AppConfig;

import java.util.List;

public class ClaudeClient implements LlmClient {

    private final AnthropicClient client;

    public ClaudeClient() {
        this.client = AnthropicOkHttpClient.fromEnv();
    }

    @Override
    public String call(String systemPrompt, String userPrompt) {
        return call(systemPrompt, "", userPrompt);
    }

    @Override
    public String call(String systemPrompt, String cacheablePrefix, String rest) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(Model.of(AppConfig.ANTHROPIC_MODEL))
                .maxTokens(8192L)
                .system(systemPrompt);

        if (cacheablePrefix.isBlank()) {
            builder.addUserMessage(rest);
        } else {
            // Cache breakpoint covers system + this block, so repeated calls that share the same
            // system prompt and cacheablePrefix (e.g. the job requirements list reused across the
            // competency-reorder call and every employer's bullet-scoring call) hit the cache
            // instead of being billed as fresh input tokens, even though `rest` differs each time.
            TextBlockParam cached = TextBlockParam.builder()
                    .text(cacheablePrefix)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build();
            TextBlockParam remainder = TextBlockParam.builder()
                    .text(rest)
                    .build();
            builder.addUserMessageOfBlockParams(List.of(
                    ContentBlockParam.ofText(cached),
                    ContentBlockParam.ofText(remainder)));
        }

        Message response = client.messages().create(builder.build());
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElse("");
    }
}
