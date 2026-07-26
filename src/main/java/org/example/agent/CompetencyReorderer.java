package org.example.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.llm.LlmClient;
import org.example.llm.JsonUtil;
import org.example.model.CompetencyCategory;
import org.example.model.JobRequirement;

import java.util.*;
import java.util.stream.Collectors;

public class CompetencyReorderer {

    private final LlmClient claude;

    public CompetencyReorderer(LlmClient claude) {
        this.claude = claude;
    }

    public List<CompetencyCategory> reorderAndFilter(List<JobRequirement> requirements,
                                                      List<CompetencyCategory> current) {
        String raw = claude.call(PromptTemplates.SYSTEM_JSON,
                                 PromptTemplates.reorderAndFilterCompetencies(requirements, current));
        String json = JsonUtil.stripFences(raw);
        System.out.println("\n  ┌─ LLM #2 reorder competencies");
        for (String line : json.lines().toList()) System.out.println("  │ " + line);
        System.out.println("  └─────");

        try {
            List<Map<String, String>> items = JsonUtil.parse(json, new TypeReference<>() {});
            List<CompetencyCategory> llmResult = items.stream()
                    .map(m -> new CompetencyCategory(
                            m.getOrDefault("category", ""),
                            m.getOrDefault("skills", "")))
                    .filter(c -> !c.category().isEmpty() && !c.skills().isBlank())
                    .toList();

            if (llmResult.isEmpty()) {
                System.err.println("[warn] Competency reorder returned empty; using original");
                return current;
            }

            // Enforce HIGH-priority skills appear first within their category
            List<CompetencyCategory> enforced = llmResult.stream()
                    .map(cat -> promoteHighPrioritySkills(cat, requirements))
                    .collect(Collectors.toList());

            System.out.println("  Final order:");
            for (CompetencyCategory c : enforced) {
                System.out.println("    " + c.category() + ": " + c.skills());
            }
            return enforced;

        } catch (Exception e) {
            System.err.println("[warn] Competency reorder parse error: " + e.getMessage() + "; using original");
            return current;
        }
    }

    /**
     * Ensures any skill keyword that appears in a HIGH requirement is moved to the
     * front of the comma-separated skills string for that category.
     */
    private CompetencyCategory promoteHighPrioritySkills(CompetencyCategory cat,
                                                          List<JobRequirement> requirements) {
        List<String> highKeywords = requirements.stream()
                .filter(r -> r.priority() == JobRequirement.Priority.HIGH)
                .map(r -> r.description().toLowerCase())
                .toList();

        List<String> skills = Arrays.stream(cat.skills().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));

        // Score each skill: how many HIGH requirement keywords does it match?
        List<String> promoted = new ArrayList<>();
        List<String> rest     = new ArrayList<>();

        for (String skill : skills) {
            String lower = skill.toLowerCase();
            boolean isHigh = highKeywords.stream()
                    .anyMatch(kw -> kw.contains(lower) || lower.contains(
                            // extract the core word from the skill (e.g. "Java 8/11/14" → "java")
                            lower.split("[ /]")[0]));
            if (isHigh) promoted.add(skill);
            else        rest.add(skill);
        }

        if (promoted.isEmpty()) return cat; // nothing to change

        List<String> ordered = new ArrayList<>(promoted);
        ordered.addAll(rest);
        return new CompetencyCategory(cat.category(), String.join(", ", ordered));
    }
}
