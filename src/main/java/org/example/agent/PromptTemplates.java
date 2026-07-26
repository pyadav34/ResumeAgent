package org.example.agent;

import org.example.model.CompetencyCategory;
import org.example.model.JobRequirement;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PromptTemplates {

    public static final String SYSTEM_JSON =
            "You are a professional resume expert. Always respond with valid JSON only — " +
            "no markdown fences, no explanation, no extra text.";

    public static final String SYSTEM_BULLET_MOD =
            "You are a professional resume editor. Preserve sentence structure exactly. " +
            "Return only the modified bullet text — no explanation, no quotes, no prefix.";

    public static final String SYSTEM_SUMMARY =
            "You are a senior resume writer with 20 years of experience. " +
            "Write naturally and specifically like a real human, never like an AI. " +
            "Return only the summary paragraph — no labels, no quotes, no explanation.";

    public static String extractRequirements(String jdText) {
        return """
                Extract key requirements from this job description.

                STRICT RULES — you MUST follow every one:
                - ONLY include skills, technologies, and experience explicitly stated in the job description text
                - Do NOT infer, generalize, or add anything not directly written in the JD
                - Do NOT include a technology just because it commonly appears in similar roles
                - If a technology is not mentioned by name in the JD, it must NOT appear in the output

                PRIORITY RULES:
                - "HIGH": listed under required skills or core technical skills sections
                - "MED": described as preferred, or mentioned as experience (not under a "required" heading)
                - "LOW": explicitly called out as nice-to-have or bonus

                Return a JSON array. Each item must have:
                  "description": a concise requirement (10-20 words)
                  "priority": "HIGH", "MED", or "LOW"

                Job Description:
                """ + jdText + """

                Return ONLY the JSON array.
                """;
    }

    public static String tailorSummary(String originalSummary, List<JobRequirement> requirements) {
        String highReqs = requirements.stream()
                .filter(r -> r.priority() == JobRequirement.Priority.HIGH)
                .map(r -> "- " + r.description())
                .collect(Collectors.joining("\n"));
        return """
                Rewrite this professional summary to align with the job requirements below.

                STRICT RULES — read carefully:
                - Sound like a real engineer wrote it, not a language model or career coach
                - No buzzwords: "dynamic", "passionate", "results-driven", "leverage", "synergy", "spearhead"
                - No vague claims — be specific and grounded
                - Maximum 3 lines — do not exceed this under any circumstances
                - Only reference technologies and experience already present in the original
                - Naturally weave in 2–3 key terms from the HIGH priority requirements below
                - Do not invent metrics, achievements, or claims not in the original
                - Write in third-person implied style (no "I" — just start with the role or skill)

                Original Summary:
                """ + originalSummary + """

                HIGH Priority Job Requirements:
                """ + highReqs + """

                Return ONLY the rewritten paragraph.
                """;
    }

    public static String reorderAndFilterCompetencies(List<JobRequirement> requirements,
                                                       List<CompetencyCategory> categories) {
        String reqs = requirements.stream()
                .map(r -> "- [" + r.priority() + "] " + r.description())
                .collect(Collectors.joining("\n"));
        String cats = categories.stream()
                .map(c -> "{\"category\": \"" + c.category() + "\", \"skills\": \"" + c.skills() + "\"}")
                .collect(Collectors.joining(",\n  ", "[\n  ", "\n]"));
        return """
                Reorder these resume skill categories and the skills within each category based on the job requirements.

                WITHIN-CATEGORY SKILL ORDERING (most important rule):
                - Skills explicitly named in HIGH priority requirements MUST come first in their category.
                - Skills named in MED requirements come next.
                - Remaining skills follow in any order.
                - Example: JD says "requires Java" → Language category becomes "Java 8/11/14, Python, TypeScript, JavaScript"
                  (Java moves to position 1, everything else stays after it)
                - Example: JD says "requires Kubernetes and Kafka" → Platform & Infra becomes "Kubernetes, Kafka, Docker, ..."

                CATEGORY ORDERING:
                - Categories whose skills most directly match HIGH priority requirements come first.

                HARD RULES:
                - Do NOT add or remove any skill or category — every item in the input must appear in the output
                - Preserve exact skill text (e.g. "Java 8/11/14" not just "Java")
                - Return the same JSON format: array of {"category": "...", "skills": "..."}
                  where "skills" is a comma-separated string in the new priority order

                Job Requirements:
                """ + reqs + """

                Current Skill Categories:
                """ + cats + """

                Return ONLY the reordered JSON array.
                """;
    }

    public static String scoreBullets(String companyName, List<String> bullets,
                                      List<JobRequirement> requirements) {
        String reqs = requirements.stream()
                .map(r -> "- [" + r.priority() + "] " + r.description())
                .collect(Collectors.joining("\n"));
        String indexedBullets = IntStream.range(0, bullets.size())
                .mapToObj(i -> i + ": " + bullets.get(i))
                .collect(Collectors.joining("\n"));
        return """
                Score each resume bullet for relevance to the job requirements below.
                Score 0-10 (10 = directly addresses a HIGH priority requirement).
                Give score >= 6 to bullets that meaningfully cover any requirement.

                Job Requirements:
                """ + reqs + """

                Employer: """ + companyName + """

                Bullets:
                """ + indexedBullets + """

                Return ONLY a JSON array: [{"index": N, "score": N, "covers": ["requirement text"]}]
                Include an entry for every bullet index from 0 to """ + (bullets.size() - 1) + ".";
    }

    public static String checkCoverage(List<JobRequirement> requirements, List<String> selectedBullets) {
        String highReqs = requirements.stream()
                .filter(r -> r.priority() == JobRequirement.Priority.HIGH)
                .map(r -> "- " + r.description())
                .collect(Collectors.joining("\n"));
        if (highReqs.isEmpty()) highReqs = "(none — all requirements are MED or LOW)";

        String bullets = IntStream.range(0, selectedBullets.size())
                .mapToObj(i -> (i + 1) + ". " + selectedBullets.get(i))
                .collect(Collectors.joining("\n"));

        return """
                Do these selected resume bullets adequately cover the HIGH priority job requirements?

                HIGH Priority Requirements:
                """ + highReqs + """

                Selected Bullets:
                """ + bullets + """

                Return ONLY JSON: {"acceptable": true/false, "coverage_score": 0-10, "uncovered": ["requirement text"]}
                "acceptable" is true when at least 70% of HIGH priority requirements are addressed.
                """;
    }

    public static String modifyBullet(String uncoveredRequirement, String originalBullet) {
        return """
                Minimally modify this resume bullet to better match the job requirement.
                STRICT RULES:
                - Preserve the exact sentence structure
                - Substitute at most 2 words with equivalent terms from the requirement
                - Do NOT add metrics, numbers, or claims not in the original bullet
                - Do NOT change the meaning or scope

                Job Requirement: """ + uncoveredRequirement + """

                Original Bullet: """ + originalBullet + """

                Return ONLY the modified bullet text.
                """;
    }
}
