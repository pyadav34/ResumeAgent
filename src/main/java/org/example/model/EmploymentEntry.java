package org.example.model;

import java.util.List;

public record EmploymentEntry(
        String companyName,
        String title,
        String startDate,
        String endDate,
        List<String> bullets
) {}
