package org.example.agent;

import org.example.model.JobRequirement;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PromptTemplates {

    public static final String SYSTEM_JSON =
            "You are a professional resume expert. Always respond with valid JSON only — " +
            "no markdown fences, no explanation, no extra text.";

    public static final String SYSTEM_BULLET_MOD =
            "You are a professional resume editor. These are resume line for employer 1.select best 12 line that matches above job requirement. make lines more human like and make it simpler. Just output response in text " +
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

    // Shared, byte-identical text reused across every employer's bullet-scoring call within a
    // run — a caller passes this as the `cacheablePrefix` to LlmClient.call(system,
    // cacheablePrefix, rest) so Anthropic bills it once per cache window instead of once per call.
    public static String requirementsBlock(List<JobRequirement> requirements) {
        String reqs = requirements.stream()
                .map(r -> "- [" + r.priority() + "] " + r.description())
                .collect(Collectors.joining("\n"));
        return "Job Requirements:\n" + reqs;
    }

    public static String scoreBullets(String companyName, List<String> bullets, int rewriteThreshold) {
        String indexedBullets = IntStream.range(0, bullets.size())
                .mapToObj(i -> i + ": " + bullets.get(i))
                .collect(Collectors.joining("\n"));
        return """

                Score each resume bullet for relevance to the job requirements above,
                and also rewrite bullets that clear the rewrite threshold below to be
                more human-like and simpler, matching the wording of the job requirement
                each one best addresses.

                SCORING:
                - Score 0-10 (10 = directly addresses a HIGH priority requirement).
                - Give score >= 6 to bullets that meaningfully cover any requirement.

                REWRITE RULES:
                - Only include a "rewritten" field for bullets scoring >= """ + rewriteThreshold + """
                . For bullets scoring below """ + rewriteThreshold + """
                , omit the "rewritten" key entirely — do not rewrite bullets that won't be used.
                - Make it read like a real engineer wrote it, not a language model
                - Keep it simple and natural
                - Do NOT add metrics, numbers, or claims not in the original bullet
                - Do NOT remove or alter any existing metric, number, or percentage from the original bullet — keep every one exactly as written
                - Do NOT change the meaning or scope

                Employer: """ + companyName + """

                Bullets:
                """ + indexedBullets + """

                Return ONLY a JSON array: [{"index": N, "score": N, "covers": ["requirement text"], "rewritten": "simplified human-like version of the bullet (omit this key for low-scoring bullets)"}]
                Include an entry for every bullet index from 0 to """ + (bullets.size() - 1) + ".";
    }

    public static String modifyBullet(String uncoveredRequirement, String originalBullet) {
        return """
                make lines more human like and make it simpler
                STRICT RULES:
                - Do NOT add metrics, numbers, or claims not in the original bullet
                - Do NOT remove or alter any existing metric, number, or percentage from the original bullet — keep every one exactly as written
                - Do NOT change the meaning or scope

                Job Requirement: """ + uncoveredRequirement + """

                Original Bullet: """ + originalBullet + """

                Return ONLY the modified bullet text.
                """;
    }
}
