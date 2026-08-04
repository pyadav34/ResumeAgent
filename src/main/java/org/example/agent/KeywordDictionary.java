package org.example.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class KeywordDictionary {

    private final List<String> terms;

    public KeywordDictionary() {
        this("tech_keywords.txt");
    }

    public KeywordDictionary(String resourceName) {
        this.terms = load(resourceName);
    }

    private List<String> load(String resourceName) {
        List<String> result = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) throw new IllegalStateException(resourceName + " not found in classpath resources");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String term = line.trim();
                if (term.isEmpty() || term.startsWith("#")) continue;
                result.add(term);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load keyword dictionary: " + resourceName, e);
        }
        return result;
    }

    /** Scans text and returns canonical dictionary terms found, in dictionary order, de-duplicated. */
    public List<String> scan(String text) {
        List<String> hits = new ArrayList<>();
        for (String term : terms) {
            if (TermMatcher.contains(text, term)) hits.add(term);
        }
        return hits;
    }
}
