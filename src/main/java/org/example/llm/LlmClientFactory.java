package org.example.llm;

import org.example.config.AppConfig;

public class LlmClientFactory {

    public static LlmClient create() {
        System.out.println("Provider : " + AppConfig.PROVIDER);
        return switch (AppConfig.PROVIDER) {
            case ANTHROPIC   -> { System.out.println("Model    : " + AppConfig.ANTHROPIC_MODEL);  yield new ClaudeClient(); }
            case OPENROUTER  -> { System.out.println("Model    : " + AppConfig.OPENROUTER_MODEL); yield new OpenRouterClient(); }
            case GEMINI      -> { System.out.println("Model    : " + AppConfig.GEMINI_MODEL);     yield new GeminiClient(); }
            case OLLAMA      -> { System.out.println("Model    : " + AppConfig.OLLAMA_MODEL);     yield new OllamaClient(); }
            case NVIDIA      -> { System.out.println("Model    : " + AppConfig.NVIDIA_MODEL);     yield new NvidiaClient(); }
        };
    }
}
