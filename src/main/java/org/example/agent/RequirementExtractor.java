package org.example.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.llm.LlmClient;
import org.example.llm.JsonUtil;
import org.example.model.JobRequirement;
import org.example.model.JobRequirement.Priority;

import java.util.List;
import java.util.Map;

public class RequirementExtractor {

    private final LlmClient claude;

    public RequirementExtractor(LlmClient claude) {
        this.claude = claude;
    }

    public List<JobRequirement> extract(String jdText) {
        String raw = claude.call(PromptTemplates.SYSTEM_JSON,
                                 PromptTemplates.extractRequirements(jdText));
        String json = JsonUtil.stripFences(raw);
        System.out.println("\n  ┌─ LLM #1 extract-requirements");
        for (String line : json.lines().toList()) System.out.println("  │ " + line);
        System.out.println("  └─────");
        List<Map<String, String>> items = JsonUtil.parse(json, new TypeReference<>() {});
        return items.stream()
                .map(m -> new JobRequirement(
                        m.get("description"),
                        parsePriority(m.getOrDefault("priority", "LOW"))))
                .toList();
    }

    private Priority parsePriority(String s) {
        try {
            return Priority.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Priority.LOW;
        }
    }
}
