package org.example.model;

import java.util.List;

public record TailoredEntry(
        String companyName,
        String title,
        String startDate,
        String endDate,
        List<String> selectedBullets
) {}
