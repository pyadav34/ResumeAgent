package org.example.agent;

import org.example.llm.LlmClient;
import org.example.llm.LlmClientFactory;
import org.example.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ResumeAgent {

    private final LlmClient claude;
    private final RequirementExtractor extractor;
    private final KeywordExtractor keywordExtractor;
    private final SummaryTailor summaryTailor;
    private final CompetencyReorderer reorderer;
    private final BulletSelectionLoop bulletLoop;

    public ResumeAgent() {
        this.claude           = LlmClientFactory.create();
        this.extractor        = new RequirementExtractor(claude);
        this.keywordExtractor = new KeywordExtractor(claude, new KeywordDictionary());
        this.summaryTailor    = new SummaryTailor(claude);
        this.reorderer        = new CompetencyReorderer(claude);
        this.bulletLoop       = new BulletSelectionLoop(claude);
    }

    public TailorResult tailor(Resume resume, String jdText) {
        System.out.println("[1] Extracting job requirements...");
        List<JobRequirement> requirements = extractor.extract(jdText);
        System.out.println("    Found " + requirements.size() + " requirements ("
                + requirements.stream().filter(r -> r.priority() == JobRequirement.Priority.HIGH).count()
                + " HIGH)");

        System.out.println("\n[1b] Extracting keywords (dictionary + LLM fallback)...");
        List<JobKeyword> keywords = keywordExtractor.extract(jdText);
        long dictCount = keywords.stream().filter(JobKeyword::fromDictionary).count();
        System.out.println("    Found " + keywords.size() + " keywords ("
                + dictCount + " from dictionary, " + (keywords.size() - dictCount) + " from LLM fallback)");

        System.out.println("\n[2] Tailoring professional summary...");
        String tailoredSummary = summaryTailor.tailor(resume.summary(), requirements);

        System.out.println("\n[3] Reordering core competencies...");
        List<CompetencyCategory> orderedCategories =
                reorderer.reorderAndFilter(requirements, resume.competencies());

        System.out.println("\n[4] Selecting bullets per employer...");
        List<TailoredEntry> entries = new ArrayList<>();
        for (EmploymentEntry emp : resume.employments()) {
            System.out.println("\n  -- " + emp.companyName()
                    + " (" + emp.bullets().size() + " bullets)");
            TailoredEntry tailored = bulletLoop.select(emp, requirements);
            System.out.println("  Selected: " + tailored.selectedBullets().size() + " bullets");
            entries.add(tailored);
        }

        System.out.println("\n[5] Checking keyword coverage...");
        String resumeText = String.join("\n", tailoredSummary,
                orderedCategories.stream().map(CompetencyCategory::skills).collect(Collectors.joining("\n")),
                entries.stream().flatMap(e -> e.selectedBullets().stream()).collect(Collectors.joining("\n")));
        KeywordCoverageReport.Result coverage = KeywordCoverageReport.evaluate(keywords, resumeText);
        System.out.println(KeywordCoverageReport.render(coverage));

        TailoredResume tailoredResume = new TailoredResume(tailoredSummary, orderedCategories, entries);
        return new TailorResult(tailoredResume, coverage);
    }
}
