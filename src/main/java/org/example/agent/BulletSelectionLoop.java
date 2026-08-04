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

    private record BulletScore(int index, int score, List<String> covers, String rewritten) {}

    private record CoverageResult(boolean acceptable, int coverageScore, List<String> uncovered) {}

    public record BulletSelectionResult(TailoredEntry entry, Map<String, List<String>> coverage) {}

    private final LlmClient claude;

    public BulletSelectionLoop(LlmClient claude) {
        this.claude = claude;
    }

    public BulletSelectionResult select(EmploymentEntry entry, List<JobRequirement> requirements) {
        List<String> all = entry.bullets();

        // Tiny employment entries: include everything, but still humanize/simplify each line
        if (all.size() <= 2) {
            String fallbackRequirement = pickRequirementFor(List.of(), requirements);
            List<String> finalBullets = new ArrayList<>();
            Map<String, List<String>> coverage = new LinkedHashMap<>();
            for (String b : all) {
                String rewritten = modifyBullet(fallbackRequirement, b);
                if (rewritten.isBlank()) rewritten = b;
                finalBullets.add(rewritten);
                coverage.put(rewritten, List.of());
                log("  [KEEP  ] " + preview(rewritten));
            }
            return new BulletSelectionResult(tailored(entry, finalBullets), coverage);
        }

        String firstBullet = all.get(0);

        // LLM #3: score all bullets once — also returns which requirements each bullet covers
        // and a humanized/simplified rewrite, so no separate LLM call is needed per bullet later.
        List<BulletScore> scores = scoreBullets(entry.companyName(), all, requirements);
        Map<String, Integer> scoreByText = new HashMap<>();
        Map<String, List<String>> coversByText = new HashMap<>();
        Map<String, String> rewrittenByText = new HashMap<>();
        for (BulletScore s : scores) {
            if (s.index() < all.size()) {
                scoreByText.put(all.get(s.index()), s.score());
                coversByText.put(all.get(s.index()), s.covers());
                rewrittenByText.put(all.get(s.index()), s.rewritten());
            }
        }

        // Log scores for all bullets
        for (int i = 0; i < all.size(); i++) {
            int score = scoreByText.getOrDefault(all.get(i), 0);
            log("  [score=" + score + "] " + preview(all.get(i)));
        }

        // Initial selection: threshold filtered, sorted by score desc, capped at MAX_BULLETS.
        // `selected` always holds ORIGINAL bullet text — final (possibly rewritten) text lives
        // in `modifications`, so we never lose track of a bullet's original position/identity.
        List<String> selected = all.stream()
                .filter(b -> scoreByText.getOrDefault(b, 0) >= AppConfig.SCORE_THRESHOLD)
                .sorted(Comparator.comparingInt((String b) -> scoreByText.getOrDefault(b, 0)).reversed())
                .limit(AppConfig.MAX_BULLETS)
                .collect(Collectors.toCollection(ArrayList::new));

        // Guarantee at least one bullet
        if (selected.isEmpty()) {
            String best = all.stream()
                    .max(Comparator.comparingInt(b -> scoreByText.getOrDefault(b, 0)))
                    .orElse(all.get(0));
            selected.add(best);
        }

        // Always keep the first bullet of each employer, even if it scored below threshold
        if (!selected.contains(firstBullet)) {
            if (selected.size() >= AppConfig.MAX_BULLETS) {
                selected.stream()
                        .min(Comparator.comparingInt(b -> scoreByText.getOrDefault(b, 0)))
                        .ifPresent(selected::remove);
            }
            selected.add(firstBullet);
            log("  [KEEP  ] (always-keep first line) " + preview(firstBullet));
        }

        // Track modifications: original text → rewritten text
        Map<String, String> modifications = new LinkedHashMap<>();

        // Coverage is computed locally from the "covers" data already returned by LLM #3 —
        // no extra LLM call needed to ask essentially the same question again.
        // LLM #4 (optional): minimally modify a bullet to close a coverage gap
        for (int iter = 0; iter < AppConfig.MAX_LOOP_ITER; iter++) {
            CoverageResult cov = computeCoverage(requirements, selected, coversByText);
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
            selected.add(candidate);

            // Minimally modify if there's an uncovered HIGH requirement
            if (!cov.uncovered().isEmpty()) {
                String req = cov.uncovered().get(0);
                String modified = modifyBullet(req, candidate);
                if (!modified.isBlank() && !modified.equals(candidate)) {
                    modifications.put(candidate, modified);
                    log("  [MODIFY ] " + preview(candidate));
                    log("         → " + preview(modified));
                    List<String> claims = new ArrayList<>(coversByText.getOrDefault(candidate, List.of()));
                    claims.add(req);
                    coversByText.put(candidate, claims);
                }
            }
        }

        // Apply the humanized/simplified rewrite already returned by LLM #3 to every selected
        // bullet that wasn't already rewritten above to close a coverage gap. LLM #3 only rewrote
        // bullets scoring at/above the threshold (to avoid paying for rewrites of bullets that get
        // discarded) — a bullet can still be selected below that score via the always-keep-first-line
        // rule, the guarantee-at-least-one fallback, or as a gap-fill candidate with no uncovered
        // requirement, so those get a one-off rewrite call here instead of being left un-humanized.
        for (String original : selected) {
            if (modifications.containsKey(original)) continue;
            String rewritten = rewrittenByText.getOrDefault(original, "");
            if (rewritten.isBlank()) {
                String requirement = pickRequirementFor(coversByText.getOrDefault(original, List.of()), requirements);
                rewritten = modifyBullet(requirement, original);
            }
            if (!rewritten.isBlank() && !rewritten.equals(original)) {
                modifications.put(original, rewritten);
                log("  [SIMPLIFY] " + preview(original));
                log("           → " + preview(rewritten));
            }
        }

        // Preserve original resume order using original bullet identity (no fuzzy text matching needed
        // now that `selected` always holds original text regardless of any later rewrite)
        List<String> orderedOriginals = all.stream().filter(selected::contains).toList();

        List<String> finalBullets = new ArrayList<>();
        Map<String, List<String>> finalCoverage = new LinkedHashMap<>();
        log("  --- final selection ---");
        for (String original : orderedOriginals) {
            String finalText = modifications.getOrDefault(original, original);
            finalBullets.add(finalText);
            finalCoverage.put(finalText, coversByText.getOrDefault(original, List.of()));
            log(modifications.containsKey(original)
                    ? "  [MODIFIED] " + preview(original)
                    : "  [KEPT    ] " + preview(original));
        }
        for (String b : all) {
            if (!selected.contains(b)) log("  [SKIPPED ] " + preview(b));
        }

        return new BulletSelectionResult(tailored(entry, finalBullets), finalCoverage);
    }

    private String pickRequirementFor(List<String> covers, List<JobRequirement> requirements) {
        if (covers != null && !covers.isEmpty()) return covers.get(0);
        return requirements.stream()
                .filter(r -> r.priority() == JobRequirement.Priority.HIGH)
                .map(JobRequirement::description)
                .findFirst()
                .or(() -> requirements.stream().map(JobRequirement::description).findFirst())
                .orElse("General role alignment");
    }

    private void log(String msg) {
        System.out.println(msg);
    }

    private String preview(String text) {
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

    private TailoredEntry tailored(EmploymentEntry e, List<String> bullets) {
        return new TailoredEntry(e.companyName(), e.title(), e.startDate(), e.endDate(), bullets, e.technologies());
    }

    private List<BulletScore> scoreBullets(String company, List<String> bullets,
                                            List<JobRequirement> requirements) {
        String raw = claude.call(PromptTemplates.SYSTEM_JSON,
                                 PromptTemplates.requirementsBlock(requirements),
                                 PromptTemplates.scoreBullets(company, bullets, AppConfig.SCORE_THRESHOLD));
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
                List<String> covers = castStringList(m.getOrDefault("covers", List.of()));
                Object rewrittenObj = m.get("rewritten");
                String rewritten = rewrittenObj != null ? rewrittenObj.toString() : "";
                result.add(new BulletScore(index, score, covers, rewritten));
            }
            return result;
        } catch (Exception e) {
            System.err.println("[warn] bullet scoring parse error for " + company + ": " + e.getMessage());
            System.err.println("       raw response: " + json);
            return List.of();
        }
    }

    private CoverageResult computeCoverage(List<JobRequirement> requirements, List<String> selected,
                                            Map<String, List<String>> coversByText) {
        List<String> highReqs = requirements.stream()
                .filter(r -> r.priority() == JobRequirement.Priority.HIGH)
                .map(JobRequirement::description)
                .toList();
        if (highReqs.isEmpty()) return new CoverageResult(true, 10, List.of());

        Set<String> covered = new HashSet<>();
        for (String bullet : selected) {
            for (String claim : coversByText.getOrDefault(bullet, List.of())) {
                for (String req : highReqs) {
                    if (RequirementMatch.matches(claim, req)) covered.add(req);
                }
            }
        }
        List<String> uncovered = highReqs.stream().filter(r -> !covered.contains(r)).toList();
        int coverageScore = Math.round(10f * covered.size() / highReqs.size());
        boolean acceptable = covered.size() >= (int) Math.ceil(highReqs.size() * 0.7);
        return new CoverageResult(acceptable, coverageScore, uncovered);
    }

    private String modifyBullet(String requirement, String bullet) {
        try {
            String result = claude.call(PromptTemplates.SYSTEM_BULLET_MOD,
                                        PromptTemplates.modifyBullet(requirement, bullet)).trim();
            logLlm("LLM modify-bullet", result);
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
