package org.example.agent;

import org.example.model.JobRequirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CoverageReport {

    public static String build(List<JobRequirement> requirements,
                                Map<String, Map<String, List<String>>> coverageByCompany) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Job Requirement Coverage Report ===\n\n");

        sb.append("--- Resume Line -> Job Requirement Mapping ---\n\n");
        for (Map.Entry<String, Map<String, List<String>>> company : coverageByCompany.entrySet()) {
            sb.append("[").append(company.getKey()).append("]\n");
            for (Map.Entry<String, List<String>> bullet : company.getValue().entrySet()) {
                sb.append("  - ").append(bullet.getKey()).append("\n");
                List<JobRequirement> matched = matchRequirements(bullet.getValue(), requirements);
                if (matched.isEmpty()) {
                    sb.append("      covers: (no direct requirement match)\n");
                } else {
                    for (JobRequirement r : matched) {
                        sb.append("      covers: [").append(r.priority()).append("] ")
                                .append(r.description()).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        sb.append("--- Missing Job Requirements (not addressed by any resume line) ---\n\n");
        List<JobRequirement> missing = requirements.stream()
                .filter(r -> !isCovered(r, coverageByCompany))
                .toList();
        if (missing.isEmpty()) {
            sb.append("  (none - every job requirement is addressed by at least one resume line)\n");
        } else {
            for (JobRequirement r : missing) {
                sb.append("  [").append(r.priority()).append("] ").append(r.description()).append("\n");
            }
        }
        return sb.toString();
    }

    private static boolean isCovered(JobRequirement requirement,
                                      Map<String, Map<String, List<String>>> coverageByCompany) {
        for (Map<String, List<String>> bullets : coverageByCompany.values()) {
            for (List<String> claims : bullets.values()) {
                for (String claim : claims) {
                    if (RequirementMatch.matches(claim, requirement.description())) return true;
                }
            }
        }
        return false;
    }

    private static List<JobRequirement> matchRequirements(List<String> claims, List<JobRequirement> requirements) {
        List<JobRequirement> matched = new ArrayList<>();
        for (String claim : claims) {
            for (JobRequirement r : requirements) {
                if (RequirementMatch.matches(claim, r.description()) && !matched.contains(r)) {
                    matched.add(r);
                }
            }
        }
        return matched;
    }
}
