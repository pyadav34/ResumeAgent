package org.example.agent;

final class RequirementMatch {

    private RequirementMatch() {}

    static boolean matches(String claim, String requirementText) {
        String a = claim.trim().toLowerCase();
        String b = requirementText.trim().toLowerCase();
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.equals(b) || a.contains(b) || b.contains(a);
    }
}
