package org.example.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.llm.LlmClient;
import org.example.llm.JsonUtil;
import org.example.model.JobKeyword;
import org.example.model.JobRequirement.Priority;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class KeywordExtractor {

    private final LlmClient claude;
    private final KeywordDictionary dictionary;

    public KeywordExtractor(LlmClient claude, KeywordDictionary dictionary) {
        this.claude = claude;
        this.dictionary = dictionary;
    }

    public List<JobKeyword> extract(String jdText) {
        List<String> dictHits = dictionary.scan(jdText);

        String raw = claude.call(PromptTemplates.SYSTEM_JSON,
                                 PromptTemplates.classifyKeywords(jdText, dictHits));
        String json = JsonUtil.stripFences(raw);
        System.out.println("\n  ┌─ LLM keyword-classify (dictionary hits: " + dictHits.size() + ")");
        for (String line : json.lines().toList()) System.out.println("  │ " + line);
        System.out.println("  └─────");

        Map<String, Priority> priorityByTerm = new LinkedHashMap<>();
        try {
            List<Map<String, String>> items = JsonUtil.parse(json, new TypeReference<>() {});
            if (items != null) {
                for (Map<String, String> m : items) {
                    if (m == null) continue;
                    String term = m.get("term");
                    if (term == null || term.isBlank()) continue;
                    priorityByTerm.put(term.trim(), parsePriority(m.getOrDefault("priority", "MED")));
                }
            }
        } catch (Exception e) {
            System.err.println("[warn] keyword classification parse error: " + e.getMessage());
            System.err.println("       raw response: " + json);
        }

        List<JobKeyword> result = new ArrayList<>();
        TreeSet<String> added = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // Dictionary hits are non-negotiable: every one is included regardless of what the LLM
        // returned, so a dropped or misclassified LLM response can never lose a keyword that's
        // provably present in the JD text (default to MED if the LLM omitted its priority call).
        for (String term : dictHits) {
            if (!added.add(term)) continue;
            Priority priority = priorityByTerm.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(term))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(Priority.MED);
            result.add(new JobKeyword(term, priority, true));
        }

        // Anything the LLM found beyond the dictionary (e.g. an obscure product name) gets added too
        for (Map.Entry<String, Priority> e : priorityByTerm.entrySet()) {
            if (!added.add(e.getKey())) continue;
            result.add(new JobKeyword(e.getKey(), e.getValue(), false));
        }

        return result;
    }

    private Priority parsePriority(String s) {
        try {
            return Priority.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Priority.MED;
        }
    }
}
