package org.example.agent;

import org.example.model.TailoredResume;

public record TailorResult(TailoredResume resume, KeywordCoverageReport.Result keywordCoverage) {}
