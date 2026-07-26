package org.example.model;

import java.util.List;

public record TailoredResume(
        String tailoredSummary,
        List<CompetencyCategory> orderedCategories,
        List<TailoredEntry> entries
) {}
