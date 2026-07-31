package org.example.agent;

import org.example.llm.LlmClient;
import org.example.llm.LlmClientFactory;
import org.example.model.*;

import java.util.ArrayList;
import java.util.List;

public class ResumeAgent {

    private final LlmClient claude;
    private final RequirementExtractor extractor;
    private final SummaryTailor summaryTailor;
    private final CompetencyReorderer reorderer;
    private final BulletSelectionLoop bulletLoop;

    public ResumeAgent() {
        this.claude        = LlmClientFactory.create();
        this.extractor     = new RequirementExtractor(claude);
        this.summaryTailor = new SummaryTailor(claude);
        this.reorderer     = new CompetencyReorderer(claude);
        this.bulletLoop    = new BulletSelectionLoop(claude);
    }

    public TailoredResume tailor(Resume resume, String jdText) {
        System.out.println("[1] Extracting job requirements...");
        List<JobRequirement> requirements = extractor.extract(jdText);
        System.out.println("    Found " + requirements.size() + " requirements ("
                + requirements.stream().filter(r -> r.priority() == JobRequirement.Priority.HIGH).count()
                + " HIGH)");

        String tailoredSummary;
        if (resume.summary() == null || resume.summary().isBlank()) {
            System.out.println("\n[2] No professional summary in resume — skipping.");
            tailoredSummary = "";
        } else {
            System.out.println("\n[2] Tailoring professional summary...");
            tailoredSummary = summaryTailor.tailor(resume.summary(), requirements);
        }

        List<CompetencyCategory> orderedCategories;
        if (resume.competencies().isEmpty()) {
            System.out.println("\n[3] No core competencies in resume — skipping.");
            orderedCategories = List.of();
        } else {
            System.out.println("\n[3] Reordering core competencies...");
            orderedCategories = reorderer.reorderAndFilter(requirements, resume.competencies());
        }

        System.out.println("\n[4] Selecting bullets per employer...");
        List<TailoredEntry> entries = new ArrayList<>();
        for (EmploymentEntry emp : resume.employments()) {
            System.out.println("\n  -- " + emp.companyName()
                    + " (" + emp.bullets().size() + " bullets)");
            TailoredEntry tailored = bulletLoop.select(emp, requirements);
            System.out.println("  Selected: " + tailored.selectedBullets().size() + " bullets");
            entries.add(tailored);
        }

        return new TailoredResume(tailoredSummary, orderedCategories, entries, resume.competenciesAtEnd());
    }
}
