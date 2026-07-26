package org.example.agent;

import org.example.llm.LlmClient;
import org.example.model.JobRequirement;

import java.util.List;

public class SummaryTailor {

    private final LlmClient claude;

    public SummaryTailor(LlmClient claude) {
        this.claude = claude;
    }

    public String tailor(String originalSummary, List<JobRequirement> requirements) {
        String result = claude.call(
                PromptTemplates.SYSTEM_SUMMARY,
                PromptTemplates.tailorSummary(originalSummary, requirements)
        ).trim();

        System.out.println("\n  ┌─ LLM summary-tailor");
        for (String line : result.lines().toList()) System.out.println("  │ " + line);
        System.out.println("  └─────");

        return result.isBlank() ? originalSummary : result;
    }
}
