package org.example.parser;

import org.example.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ResumeParser {

    public Resume parse(InputStream input) throws IOException {
        String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        return parse(text);
    }

    public Resume parse(String text) {
        List<String> lines = text.lines().toList();

        // Sections are optional and may appear in any order relative to one another
        // (e.g. "Core Competencies" may come before the first employer or after the last one).
        // Each marker line is recognized regardless of the current section, so a missing
        // "Professional Summary" / "Core Competencies" header doesn't strand later lines
        // (like employer entries) in the wrong section.
        enum Section { CONTACT, SUMMARY, COMPETENCIES, EMPLOYER }
        Section section = Section.CONTACT;

        List<String> contactLines = new ArrayList<>();
        StringBuilder summaryBuilder = new StringBuilder();
        List<CompetencyCategory> competencies = new ArrayList<>();
        List<EmploymentEntry> employments = new ArrayList<>();

        String currentCompany = null, currentTitle = null, currentStart = null, currentEnd = null;
        String currentTechnologies = null;
        List<String> currentBullets = new ArrayList<>();

        // Whether "Core Competencies" was declared after at least one employer has already
        // been seen — output should mirror that position (start vs. end of the resume).
        boolean competenciesAtEnd = false;

        for (String raw : lines) {
            String trimmed = raw.trim();

            if (trimmed.equals("Professional Summary")) {
                if (currentCompany != null) {
                    employments.add(new EmploymentEntry(
                            currentCompany, currentTitle, currentStart, currentEnd,
                            List.copyOf(currentBullets), currentTechnologies));
                    currentCompany = null;
                }
                section = Section.SUMMARY;
                continue;
            }
            if (trimmed.equals("Core Competencies")) {
                if (currentCompany != null) {
                    employments.add(new EmploymentEntry(
                            currentCompany, currentTitle, currentStart, currentEnd,
                            List.copyOf(currentBullets), currentTechnologies));
                    currentCompany = null;
                }
                competenciesAtEnd = !employments.isEmpty();
                section = Section.COMPETENCIES;
                continue;
            }
            if (isEmployerHeader(trimmed)) {
                if (currentCompany != null) {
                    employments.add(new EmploymentEntry(
                            currentCompany, currentTitle, currentStart, currentEnd,
                            List.copyOf(currentBullets), currentTechnologies));
                }
                String[] parts = splitEmployer(trimmed);
                currentCompany = parts[0];
                currentTitle   = parts[1];
                currentStart   = parts[2];
                currentEnd     = parts[3];
                currentBullets = new ArrayList<>();
                currentTechnologies = null;
                section = Section.EMPLOYER;
                continue;
            }

            switch (section) {
                case CONTACT -> contactLines.add(trimmed);
                case SUMMARY -> {
                    if (!trimmed.isEmpty()) {
                        if (!summaryBuilder.isEmpty()) summaryBuilder.append(" ");
                        summaryBuilder.append(trimmed);
                    }
                }
                case COMPETENCIES -> {
                    if (trimmed.matches("[A-Za-z /&]+:\\s+.+")) {
                        String[] kv = trimmed.split(":\\s+", 2);
                        competencies.add(new CompetencyCategory(kv[0].trim(), kv[1].trim()));
                    }
                }
                case EMPLOYER -> {
                    if (isTechStackLine(trimmed)) {
                        currentTechnologies = trimmed.split(":\\s*", 2)[1].trim();
                    } else if (!trimmed.isEmpty()) {
                        currentBullets.add(trimmed);
                    }
                }
            }
        }

        if (currentCompany != null && !currentBullets.isEmpty()) {
            employments.add(new EmploymentEntry(
                    currentCompany, currentTitle, currentStart, currentEnd,
                    List.copyOf(currentBullets), currentTechnologies));
        }

        Contact contact = parseContact(contactLines);
        String summary = summaryBuilder.toString().trim();

        return new Resume(contact, summary, List.copyOf(competencies), List.copyOf(employments), competenciesAtEnd);
    }

    // A trailing "Development Environment: ..." / "Technologies: ..." line lists the tech
    // stack for the job rather than an accomplishment — it's kept verbatim and rendered
    // separately instead of being scored/selected as a bullet.
    private boolean isTechStackLine(String line) {
        return line.matches("(?i)(development environment|technologies|tech stack|environment)\\s*:.+");
    }

    private boolean isEmployerHeader(String line) {
        if (line.isEmpty()) return false;
        String[] parts = line.split("\\|");
        return parts.length >= 3;
    }

    private String[] splitEmployer(String line) {
        String[] parts = line.split("\\s*\\|\\s*");
        String company   = parts[0].trim();
        String title     = parts.length > 1 ? parts[1].trim() : "";
        String dateRange = parts.length > 2 ? parts[2].trim() : "";
        String[] dates   = dateRange.split("\\s*[-–—]\\s*", 2);
        String start = dates[0].trim();
        String end   = dates.length > 1 ? dates[1].trim() : "Present";
        return new String[]{company, title, start, end};
    }

    private Contact parseContact(List<String> lines) {
        String name = null, email = null, phone = null, address = null;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            if (line.contains("@") && email == null) {
                email = line;
            } else if (line.matches(".*\\d{3}.*\\d{3}.*\\d{4}.*") && phone == null) {
                phone = line;
            } else if (name == null) {
                name = line;
            } else if (address == null) {
                address = line;
            }
        }
        return new Contact(name, email, phone, address);
    }
}
