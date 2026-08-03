package com.mkpro.facts;

import java.util.*;

/**
 * FactEngine — orchestrator combining math verification and relationship validation.
 *
 * Pre-turn: injects relevant formulas and relationships into agent context.
 * Post-turn: validates mathematical claims and relationship assertions.
 * On-demand: agents call verify_fact tool.
 */
public class FactEngine {

    private final FactStore store;
    private final FactClassifier classifier;
    private final GroovyFactEvaluator evaluator;
    private final RelationshipGraph graph;
    private final RelationshipValidator validator;

    public FactEngine() {
        this.store = new FactStore();
        this.store.load();

        this.classifier = new FactClassifier(store);
        this.evaluator = new GroovyFactEvaluator();
        this.graph = new RelationshipGraph();
        this.validator = new RelationshipValidator(graph);

        // Build relationship graph from loaded triples
        for (RelationshipTriple triple : store.getAllRelationships()) {
            graph.addTriple(triple);
        }
        validator.initFromStore(store);
    }

    /**
     * PRE-TURN: Get relevant facts to inject into agent stimulus.
     * Returns formatted text with formulas and relationships.
     */
    public String getRelevantFacts(String text) {
        if (text == null || text.isBlank()) return null;

        StringBuilder sb = new StringBuilder();

        // Math facts
        List<MathFact> mathFacts = classifier.findRelevantMathFacts(text);
        if (!mathFacts.isEmpty()) {
            sb.append("[VERIFIED FACTS]\n");
            for (MathFact fact : mathFacts) {
                sb.append("  • ").append(fact.getFormula());
                if (fact.getUnits() != null && !fact.getUnits().isEmpty()) {
                    sb.append(" (units: ").append(fact.getUnits()).append(")");
                }
                sb.append("\n");
            }
        }

        // Relationship facts
        List<RelationshipTriple> rels = classifier.findRelevantRelationships(text);
        if (!rels.isEmpty()) {
            if (sb.length() == 0) sb.append("[VERIFIED FACTS]\n");
            Set<String> seen = new HashSet<>();
            for (RelationshipTriple rel : rels) {
                String line = rel.getSubject() + " " + rel.getPredicate() + " " + rel.getObject();
                if (seen.add(line)) {
                    sb.append("  • ").append(line).append("\n");
                }
                if (seen.size() >= 5) break; // Cap at 5 relationships to avoid noise
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * POST-TURN: Validate agent response for math errors and relationship conflicts.
     * Returns list of issues found (empty = all good).
     */
    public List<String> validateResponse(String response) {
        if (response == null || response.isBlank()) return Collections.emptyList();

        List<String> issues = new ArrayList<>();

        // Validate relationship claims
        List<String> relIssues = validator.validateClaim(response);
        issues.addAll(relIssues);

        return issues;
    }

    /**
     * ON-DEMAND: Verify a specific mathematical fact with given variables.
     * Called by the verify_fact agent tool.
     */
    public Map<String, Object> verifyMath(String factKey, Map<String, Object> variables) {
        MathFact fact = store.getMathFact(factKey);
        if (fact == null) {
            // Try partial key match (e.g., "circle_area" matches "geometry.circle_area")
            for (MathFact mf : store.getAllMathFacts()) {
                if (mf.getKey().endsWith("." + factKey) || mf.getKey().equals(factKey)) {
                    fact = mf;
                    break;
                }
            }
        }
        if (fact == null) {
            // Try keyword search (split underscores into separate keywords)
            String[] parts = factKey.replace("_", " ").split("\\s+");
            List<MathFact> found = store.findByKeywords(java.util.Arrays.asList(parts));
            if (!found.isEmpty()) {
                fact = found.get(0);
            } else {
                return Map.of("error", "Unknown fact: " + factKey + ". Use /facts math to list available facts.");
            }
        }
        return evaluator.verify(fact, variables);
    }

    /**
     * ON-DEMAND: Validate a claimed result against a known formula.
     */
    public Map<String, Object> validateMath(String factKey, Map<String, Object> variables) {
        MathFact fact = store.getMathFact(factKey);
        if (fact == null) {
            // Try partial key match
            for (MathFact mf : store.getAllMathFacts()) {
                if (mf.getKey().endsWith("." + factKey) || mf.getKey().equals(factKey)) {
                    fact = mf;
                    break;
                }
            }
        }
        if (fact == null) {
            String[] parts = factKey.replace("_", " ").split("\\s+");
            List<MathFact> found = store.findByKeywords(java.util.Arrays.asList(parts));
            if (!found.isEmpty()) fact = found.get(0);
            else return Map.of("error", "Unknown fact: " + factKey);
        }
        return evaluator.validate(fact, variables);
    }

    /**
     * ON-DEMAND: Check a relationship.
     */
    public Map<String, Object> checkRelationship(String subject, String predicate, String object) {
        boolean direct = validator.check(subject, predicate, object);
        if (direct) {
            return Map.of("verified", true, "type", "direct", "chain", List.of(subject, object));
        }

        List<String> chain = validator.checkTransitive(subject, predicate, object);
        if (!chain.isEmpty()) {
            return Map.of("verified", true, "type", "transitive", "chain", chain);
        }

        return Map.of("verified", false, "message", "No known relationship: " + subject + " " + predicate + " " + object);
    }

    /**
     * ON-DEMAND: Query all relationships for a subject.
     */
    public List<String> queryRelationships(String subject) {
        List<RelationshipGraph.Edge> edges = validator.getAllRelationships(subject);
        List<String> results = new ArrayList<>();
        for (RelationshipGraph.Edge e : edges) {
            results.add(subject + " " + e.predicate + " " + e.target);
        }
        return results;
    }

    // ═══ Accessors ═══

    public FactStore getStore() { return store; }
    public FactClassifier getClassifier() { return classifier; }
    public RelationshipValidator getValidator() { return validator; }
    public RelationshipGraph getGraph() { return graph; }

    /**
     * Add a relationship at runtime with confidence score.
     * Used by FactExtractor when Knowledge Scheduler discovers relationships from docs.
     */
    public void addRelationship(String subject, String predicate, String object, String domain, double confidence) {
        RelationshipTriple triple = new RelationshipTriple();
        triple.setSubject(subject);
        triple.setPredicate(predicate);
        triple.setObject(object);
        triple.setDomain(domain);

        // Check for contradiction before adding
        String contradiction = graph.detectContradiction(subject, predicate, object);
        if (contradiction != null) {
            System.out.println("\u001b[33m  [FactEngine] Skipped contradicting fact: " + subject + " " + predicate + " " + object + " (" + contradiction + ")\u001b[0m");
            return;
        }

        store.addRelationship(triple);
        graph.addTriple(triple, confidence);
    }

    /**
     * Get count of dynamically extracted facts (confidence < 1.0).
     */
    public int extractedFactCount() {
        int count = 0;
        for (var edges : graph.getAllEdges()) {
            if (edges.confidence < 1.0) count++;
        }
        return count;
    }

    public void shutdown() {
        evaluator.shutdown();
    }

    /**
     * Summary stats for display.
     */
    public String getStats() {
        return "FactEngine: " + store.mathFactCount() + " math facts, " +
               store.relationshipCount() + " relationships (" +
               graph.nodeCount() + " nodes, " + graph.edgeCount() + " edges)";
    }
}
