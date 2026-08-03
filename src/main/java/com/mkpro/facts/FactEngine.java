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
            // Try to find by keyword
            List<MathFact> found = store.findByKeywords(List.of(factKey));
            if (!found.isEmpty()) {
                fact = found.get(0);
            } else {
                return Map.of("error", "Unknown fact: " + factKey);
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
            List<MathFact> found = store.findByKeywords(List.of(factKey));
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
