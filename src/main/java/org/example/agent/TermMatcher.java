package org.example.agent;

import java.util.regex.Pattern;

final class TermMatcher {

    private TermMatcher() {}

    static boolean contains(String haystack, String term) {
        if (haystack == null || haystack.isBlank() || term == null || term.isBlank()) return false;
        String pattern = "(?<![A-Za-z0-9])" + Pattern.quote(term.trim()) + "(?![A-Za-z0-9])";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(haystack).find();
    }
}