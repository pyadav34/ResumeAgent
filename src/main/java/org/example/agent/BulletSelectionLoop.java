package org.example.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.config.AppConfig;
import org.example.llm.LlmClient;
import org.example.llm.JsonUtil;
import org.example.model.EmploymentEntry;
import org.example.model.JobRequirement;
import org.example.model.TailoredEntry;

import java.util.*;
import java.util.stream.Collectors;

public class BulletSelectionLoop {

    private record BulletScore(int index, int score) {}

    private record CoverageResult(boolean acceptable, int coverageScore, List<String> uncovered) {}

    private final LlmClient claude;

    public BulletSelectionLoop(LlmClient claude) {
        this.claude = claude;
    }

    public TailoredEntry select(EmploymentEntry entry, List<JobRequirement> requirements) {
        List<String> all = entry.bullets();

        // Tiny employment entries: include everything
        if (all.size() <= 2) {
            for (String b : all) log("  [KEEP  ] " + preview(b));
            return tailored(entry, new ArrayList<>(all));
        }

        // LLM #3: score all bullets once
        List<BulletScore> scores = scoreBullets(entry.companyName(), all, requirements);
        Map<String, Integer> scoreByText = new HashMap<>();
        for (BulletScore s : scores) {
            if (s.index() < all.size()) scoreByText.put(all.get(s.index()), s.score());
        }

        // Log scores for all bullets
        for (int i = 0; i < all.size(); i++) {
            int score = scoreByText.getOrDefault(all.get(i), 0);
            log("  [score=" + score + "] " + preview(all.get(i)));
        }

        // Initial selection: threshold filtered, sorted by score desc, capped at MAX_BULLETS
        List<String> selected = all.stream()
                .filter(b -> scoreByText.getOrDefault(b, 0) >= AppConfig.SCORE_THRESHOLD)
                .sorted(Comparator.comparingInt((String b) -> scoreByText.getOrDefault(b, 0)).reversed())
                .limit(AppConfig.MAX_BULLETS)
                .collect(Collectors.toCollection(ArrayList::new));

        // Guarantee at least one bullet
        if (selected.isEmpty() && !all.isEmpty()) {
            String best = all.stream()
                    .max(Comparator.comparingInt(b -> scoreByText.getOrDefault(b, 0)))
                    .orElse(all.get(0));
            selected.add(best);
        }

        // Track LLM #5 modifications: original text → modified text
        Map<String, String> modifications = new LinkedHashMap<>();

        // LLM #4 + optional LLM #5 loop
        for (int iter = 0; iter < AppConfig.MAX_LOOP_ITER; iter++) {
            CoverageResult cov = checkCoverage(requirements, selected);
            log("  [coverage iter=" + (iter + 1) + "] score=" + cov.coverageScore()
                    + " acceptable=" + cov.acceptable()
                    + (cov.uncovered().isEmpty() ? "" : " uncovered: " + cov.uncovered().get(0)));
            if (cov.acceptable()) break;
            if (iter == AppConfig.MAX_LOOP_ITER - 1) break;

            // Find highest-scoring unselected bullet
            List<String> unselected = all.stream()
                    .filter(b -> !selected.contains(b))
                    .sorted(Comparator.comparingInt((String b) -> scoreByText.getOrDefault(b, 0)).reversed())
                    .toList();

            if (unselected.isEmpty()) break;
            if (selected.size() >= AppConfig.MAX_BULLETS) break;

            String candidate = unselected.get(0);

            // LLM #5: minimally modify if there's an uncovered HIGH requirement
            if (!cov.uncovered().isEmpty()) {
                String req = cov.uncovered().get(0);
                String modified = modifyBullet(req, candidate);
                if (!modified.isBlank() && !modified.equals(candidate)) {
                    modifications.put(candidate, modified);
                    log("  [MODIFY ] " + preview(candidate));
                    log("         → " + preview(modified));
                    selected.add(modified);
                } else {
                    selected.add(candidate);
                }
            } else {
                selected.add(candidate);
            }
        }

        // Preserve original resume order
        List<String> ordered = all.stream()
                .filter(b -> selected.stream().anyMatch(s -> s.equals(b) || textMatch(s, b)))
                .toList();
        // Append any LLM-modified bullets not in original order
        for (String s : selected) {
            if (!all.contains(s)) ordered = append(ordered, s);
        }

        // Log final keep/skip decisions
        Set<String> selectedSet = new HashSet<>(selected);
        log("  --- final selection ---");
        for (String b : all) {
            boolean kept = selectedSet.contains(b)
                    || ordered.stream().anyMatch(o -> textMatch(o, b));
            if (kept) {
                String modified = modifications.get(b);
                if (modified != null) {
                    log("  [MODIFIED] " + preview(b));
                } else {
                    log("  [KEPT    ] " + preview(b));
                }
            } else {
                log("  [SKIPPED ] " + preview(b));
            }
        }

        return tailored(entry, ordered);
    }

    private void log(String msg) {
        System.out.println(msg);
    }

    private String preview(String text) {
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

    private boolean textMatch(String a, String b) {
        // Modified bullets may differ slightly; check if starts the same way
        if (a.equals(b)) return true;
        int minLen = Math.min(30, Math.min(a.length(), b.length()));
        return minLen > 10 && a.substring(0, minLen).equalsIgnoreCase(b.substring(0, minLen));
    }

    private List<String> append(List<String> list, String item) {
        List<String> result = new ArrayList<>(list);
        result.add(item);
        return result;
    }

    private TailoredEntry tailored(EmploymentEntry e, List<String> bullets) {
        return new TailoredEntry(e.companyName(), e.title(), e.startDate(), e.endDate(), bullets, e.technologies());
    }

    private List<BulletScore> scoreBullets(String company, List<String> bullets,
                                            List<JobRequirement> requirements) {
        String raw = claude.call(PromptTemplates.SYSTEM_JSON,
                                 PromptTemplates.scoreBullets(company, bullets, requirements));
        String json = JsonUtil.stripFences(raw);
        logLlm("LLM #3 score-bullets (" + company + ")", json);
        try {
            List<Map<String, Object>> items = JsonUtil.parse(json, new TypeReference<>() {});
            if (items == null) {
                System.err.println("[warn] bullet scoring returned null for " + company + "; raw: " + json);
                return List.of();
            }
            List<BulletScore> result = new ArrayList<>();
            for (Map<String, Object> m : items) {
                if (m == null) continue;
                int index = ((Number) m.getOrDefault("index", 0)).intValue();
                int score = ((Number) m.getOrDefault("score", 0)).intValue();
                result.add(new BulletScore(index, score));
            }
            return result;
        } catch (Exception e) {
            System.err.println("[warn] bullet scoring parse error for " + company + ": " + e.getMessage());
            System.err.println("       raw response: " + json);
            return List.of();
        }
    }

    private CoverageResult checkCoverage(List<JobRequirement> requirements, List<String> selected) {
        String raw = claude.call(PromptTemplates.SYSTEM_JSON,
                                 PromptTemplates.checkCoverage(requirements, selected));
        String json = JsonUtil.stripFences(raw);
        logLlm("LLM #4 coverage-check", json);
        try {
            Map<String, Object> m = JsonUtil.parse(json, new TypeReference<>() {});
            if (m == null) {
                System.err.println("[warn] coverage check returned null; assuming acceptable");
                return new CoverageResult(true, 5, List.of());
            }
            boolean acceptable     = Boolean.TRUE.equals(m.get("acceptable"));
            int coverageScore      = ((Number) m.getOrDefault("coverage_score", 0)).intValue();
            List<String> uncovered = castStringList(m.getOrDefault("uncovered", List.of()));
            return new CoverageResult(acceptable, coverageScore, uncovered);
        } catch (Exception e) {
            System.err.println("[warn] coverage check parse error: " + e.getMessage());
            System.err.println("       raw response: " + json);
            return new CoverageResult(true, 5, List.of());
        }
    }

    private String modifyBullet(String requirement, String bullet) {
        try {
            String result = claude.call(PromptTemplates.SYSTEM_BULLET_MOD,
                                        PromptTemplates.modifyBullet(requirement, bullet)).trim();
            logLlm("LLM #5 modify-bullet", result);
            return result;
        } catch (Exception e) {
            System.err.println("[warn] bullet modification failed: " + e.getMessage());
            return bullet;
        }
    }

    private void logLlm(String label, String response) {
        System.out.println("\n  ┌─ " + label);
        for (String line : response.lines().toList()) {
            System.out.println("  │ " + line);
        }
        System.out.println("  └─────");
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
