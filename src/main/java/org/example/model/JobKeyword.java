package org.example.model;

public record JobKeyword(String term, JobRequirement.Priority priority, boolean fromDictionary) {}