package org.example.llm;

public interface LlmClient {
    String call(String systemPrompt, String userPrompt);
}
