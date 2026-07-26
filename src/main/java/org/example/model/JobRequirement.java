package org.example.model;

public record JobRequirement(String description, Priority priority) {
    public enum Priority { HIGH, MED, LOW }
}
