package org.example.agent;

import org.example.model.JobKeyword;
import org.example.model.JobRequirement.Priority;

import java.util.ArrayList;
import java.util.List;

public class KeywordCoverageReport {

    public record Result(List<JobKeyword> covered, List<JobKeyword> missing, int score) {}

    public static Result evaluate(List<JobKeyword> keywords, String resumeText) {
        List<JobKeyword> covered = new ArrayList<>();
        List<JobKeyword> missing = new ArrayList<>();
        int weightTotal = 0;
        int weightCovered = 0;
        for (JobKeyword kw : keywords) {
            int weight = weight(kw.priority());
            weightTotal += weight;
            if (TermMatcher.contains(resumeText, kw.term())) {
                covered.add(kw);
                weightCovered += weight;
            } else {
                missing.add(kw);
            }
        }
        int score = weightTotal == 0 ? 100 : Math.round(100f * weightCovered / weightTotal);
        return new Result(covered, missing, score);
    }

    public static String render(Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Keyword Coverage Report ===\n\n");
        sb.append("Score: ").append(result.score()).append("%  (")
          .append(result.covered().size()).append("/")
          .append(result.covered().size() + result.missing().size())
          .append(" keywords present in tailored resume)\n\n");

        sb.append("--- Covered ---\n");
        for (JobKeyword k : result.covered()) {
            sb.append("  [x] [").append(k.priority()).append("] ").append(k.term()).append("\n");
        }

        sb.append("\n--- Missing ---\n");
        if (result.missing().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (JobKeyword k : result.missing()) {
                sb.append("  [ ] [").append(k.priority()).append("] ").append(k.term()).append("\n");
            }
        }
        return sb.toString();
    }

    private static int weight(Priority p) {
        return switch (p) {
            case HIGH -> 3;
            case MED -> 2;
            case LOW -> 1;
        };
    }
}
