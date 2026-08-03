package com.mkpro.facts;

import java.util.*;

/**
 * Zero-latency keyword → fact domain matching.
 * Scans input text for keywords that match known facts.
 * No LLM call — pure string matching.
 */
public class FactClassifier {

    private final FactStore store;

    public FactClassifier(FactStore store) {
        this.store = store;
    }

    /**
     * Find math facts relevant to the given text.
     * Returns facts whose keywords appear in the input.
     */
    public List<MathFact> findRelevantMathFacts(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        String lower = text.toLowerCase();
        List<String> matchedKeywords = new ArrayList<>();

        for (MathFact fact : store.getAllMathFacts()) {
            for (String keyword : fact.getKeywords()) {
                if (lower.contains(keyword)) {
                    matchedKeywords.add(keyword);
                    break;
                }
            }
        }

        if (matchedKeywords.isEmpty()) return Collections.emptyList();
        return store.findByKeywords(matchedKeywords);
    }

    /**
     * Find relationship triples relevant to the given text.
     * Matches subject or object mentions in the input.
     */
    public List<RelationshipTriple> findRelevantRelationships(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        String lower = text.toLowerCase();
        List<RelationshipTriple> results = new ArrayList<>();

        for (RelationshipTriple triple : store.getAllRelationships()) {
            if (lower.contains(triple.getSubject().toLowerCase()) ||
                lower.contains(triple.getObject().toLowerCase())) {
                results.add(triple);
            }
        }
        return results;
    }

    /**
     * Check if the text likely involves mathematical computation.
     * Used to decide whether to run post-turn validation.
     */
    public boolean likelyInvolvesMath(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();

        // Check for numerical patterns: "= 50", "equals 123.4", etc.
        if (lower.matches(".*\\d+\\.?\\d*\\s*[×*x/+\\-]\\s*\\d+.*")) return true;

        // Check for math keywords
        String[] mathSignals = {"calculate", "compute", "formula", "equation",
            "area", "volume", "distance", "radius", "velocity", "force",
            "energy", "power", "resistance", "throughput", "latency"};
        for (String signal : mathSignals) {
            if (lower.contains(signal)) return true;
        }
        return false;
    }
}
