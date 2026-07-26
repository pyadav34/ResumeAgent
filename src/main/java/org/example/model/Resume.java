package org.example.model;

import java.util.List;

public record Resume(
        Contact contact,
        String summary,
        List<CompetencyCategory> competencies,
        List<EmploymentEntry> employments
) {}
