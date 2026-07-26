package org.example.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GeminiClient implements LlmClient {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String call(String systemPrompt, String userPrompt) {
        try {
            ObjectNode body = mapper.createObjectNode();

            // System instruction
            body.putObject("system_instruction")
                    .putArray("parts")
                    .addObject()
                    .put("text", systemPrompt);

            // User message
            body.putArray("contents")
                    .addObject()
                    .put("role", "user")
                    .putArray("parts")
                    .addObject()
                    .put("text", userPrompt);

            // Generation config
            body.putObject("generationConfig").put("maxOutputTokens", 8192);

            String url = String.format(BASE_URL, AppConfig.GEMINI_MODEL, AppConfig.GEMINI_API_KEY);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Gemini error " + response.statusCode() + ": " + response.body());
            }

            return mapper.readTree(response.body())
                    .path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Gemini call failed", e);
        }
    }
}
