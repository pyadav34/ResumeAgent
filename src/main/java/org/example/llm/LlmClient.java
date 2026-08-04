package org.example.llm;

public interface LlmClient {
    String call(String systemPrompt, String userPrompt);

    /**
     * Splits the prompt into a {@code cacheablePrefix} — content that repeats identically across
     * many calls within a single run (e.g. the extracted job requirements list) — and the
     * call-specific {@code rest}. Providers that support prompt caching (Anthropic) bill the
     * prefix once per cache window instead of on every call; others fall back to plain
     * concatenation, so this is always safe to call.
     */
    default String call(String systemPrompt, String cacheablePrefix, String rest) {
        return call(systemPrompt, cacheablePrefix + rest);
    }
}
