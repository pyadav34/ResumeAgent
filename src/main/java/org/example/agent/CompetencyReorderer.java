package org.example.agent;

import org.example.model.CompetencyCategory;
import org.example.model.JobRequirement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public class CompetencyReorderer {

    // No LLM call: category and skill relevance is decided by keyword-matching against the
    // requirement text, which is already how promotePrioritySkills' predecessor overrode the
    // LLM's own within-category ordering. extractRequirements only ever emits requirement text
    // using terms explicitly named in the JD, so literal keyword matching is a safe substitute.
    public List<CompetencyCategory> reorderAndFilter(List<JobRequirement> requirements,
                                                      List<CompetencyCategory> current) {
        List<String> highKeywords = keywordsFor(requirements, JobRequirement.Priority.HIGH);
        List<String> medKeywords  = keywordsFor(requirements, JobRequirement.Priority.MED);

        // Stream.sorted() is a stable sort, so categories with equal relevance keep their
        // original relative order instead of being shuffled.
        List<CompetencyCategory> reordered = current.stream()
                .sorted(Comparator.comparingInt(
                        (CompetencyCategory c) -> -matchScore(c, highKeywords, medKeywords)))
                .map(c -> promotePrioritySkills(c, highKeywords, medKeywords))
                .toList();

        System.out.println("\n  Competency order (local, no LLM):");
        for (CompetencyCategory c : reordered) {
            System.out.println("    " + c.category() + ": " + c.skills());
        }
        return reordered;
    }

    private List<String> keywordsFor(List<JobRequirement> requirements, JobRequirement.Priority priority) {
        return requirements.stream()
                .filter(r -> r.priority() == priority)
                .map(r -> r.description().toLowerCase())
                .toList();
    }

    // HIGH matches are weighted far above MED matches, so any category with HIGH-priority
    // relevance ranks above one with only MED-priority relevance, regardless of match count.
    private int matchScore(CompetencyCategory cat, List<String> highKeywords, List<String> medKeywords) {
        List<String> skills = splitSkills(cat.skills());
        long highMatches = skills.stream().filter(s -> matchesAny(s, highKeywords)).count();
        long medMatches  = skills.stream().filter(s -> matchesAny(s, medKeywords)).count();
        return (int) (highMatches * 1000 + medMatches);
    }

    /**
     * Promotes skills matching a HIGH requirement to the front of the category, then skills
     * matching a MED requirement, leaving the rest in their original relative order.
     */
    private CompetencyCategory promotePrioritySkills(CompetencyCategory cat,
                                                      List<String> highKeywords, List<String> medKeywords) {
        List<String> skills = splitSkills(cat.skills());

        List<String> high = new ArrayList<>();
        List<String> med  = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String skill : skills) {
            if (matchesAny(skill, highKeywords))     high.add(skill);
            else if (matchesAny(skill, medKeywords)) med.add(skill);
            else                                     rest.add(skill);
        }

        if (high.isEmpty() && med.isEmpty()) return cat; // nothing to change

        List<String> ordered = new ArrayList<>(high);
        ordered.addAll(med);
        ordered.addAll(rest);
        return new CompetencyCategory(cat.category(), String.join(", ", ordered));
    }

    private List<String> splitSkills(String skills) {
        return Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private boolean matchesAny(String skill, List<String> keywords) {
        String lower = skill.toLowerCase();
        String coreWord = lower.split("[ /]")[0];
        return keywords.stream().anyMatch(kw -> containsWord(kw, lower) || containsWord(kw, coreWord));
    }

    // Whole-word match against the requirement text, so short skill names (e.g. "Go") don't
    // trivially match themselves or false-positive against unrelated words like "google"/"ongoing".
    private boolean containsWord(String text, String word) {
        if (word.isEmpty()) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }
}
