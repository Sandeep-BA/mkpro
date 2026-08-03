package com.mkpro.facts;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory directed graph of relationship triples.
 * Supports BFS transitive traversal and contradiction detection.
 * Nodes are subjects/objects, edges are labeled predicates.
 */
public class RelationshipGraph {

    // adjacency: node → list of (predicate, target)
    private final Map<String, List<Edge>> outgoing = new ConcurrentHashMap<>();
    private final Map<String, List<Edge>> incoming = new ConcurrentHashMap<>();

    // Inverse predicates for contradiction detection
    private static final Map<String, String> INVERSE_PREDICATES = Map.of(
        "requires", "required_by",
        "replaces", "replaced_by",
        "extends", "extended_by",
        "part_of", "has_part",
        "manages", "managed_by"
    );

    // Contradictory predicate pairs
    private static final Map<String, String> CONTRADICTIONS = Map.of(
        "requires", "not_requires",
        "replaces", "replaced_by"  // A replaces B means B can't replace A
    );

    public static class Edge {
        public final String predicate;
        public final String target;
        public final String domain;

        public Edge(String predicate, String target, String domain) {
            this.predicate = predicate;
            this.target = target;
            this.domain = domain;
        }

        @Override
        public String toString() {
            return "--" + predicate + "--> " + target;
        }
    }

    /**
     * Add a relationship triple to the graph.
     */
    public void addTriple(RelationshipTriple triple) {
        String subject = normalize(triple.getSubject());
        String object = normalize(triple.getObject());
        String predicate = triple.getPredicate();
        String domain = triple.getDomain();

        outgoing.computeIfAbsent(subject, k -> new ArrayList<>())
                .add(new Edge(predicate, object, domain));
        incoming.computeIfAbsent(object, k -> new ArrayList<>())
                .add(new Edge(predicate, subject, domain));
    }

    /**
     * Direct check: does subject have predicate → object?
     */
    public boolean check(String subject, String predicate, String object) {
        List<Edge> edges = outgoing.get(normalize(subject));
        if (edges == null) return false;
        String normObj = normalize(object);
        for (Edge e : edges) {
            if (e.predicate.equals(predicate) && normalize(e.target).equals(normObj)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transitive check: can we reach object from subject via predicate chains?
     * Returns the chain if found, empty list if not.
     */
    public List<String> checkTransitive(String subject, String predicate, String object) {
        String start = normalize(subject);
        String end = normalize(object);

        // BFS
        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        queue.add(List.of(start));
        visited.add(start);

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);

            List<Edge> edges = outgoing.get(current);
            if (edges == null) continue;

            for (Edge e : edges) {
                if (!e.predicate.equals(predicate)) continue;
                String next = normalize(e.target);
                if (next.equals(end)) {
                    List<String> result = new ArrayList<>(path);
                    result.add(next);
                    return result;
                }
                if (!visited.contains(next)) {
                    visited.add(next);
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(next);
                    queue.add(newPath);
                }
            }
        }
        return Collections.emptyList(); // Not reachable
    }

    /**
     * Query: get all objects reachable from subject via predicate.
     */
    public List<String> query(String subject, String predicate) {
        List<Edge> edges = outgoing.get(normalize(subject));
        if (edges == null) return Collections.emptyList();

        List<String> results = new ArrayList<>();
        for (Edge e : edges) {
            if (e.predicate.equals(predicate)) {
                results.add(e.target);
            }
        }
        return results;
    }

    /**
     * Get all relationships for a subject (all predicates).
     */
    public List<Edge> getRelationships(String subject) {
        List<Edge> edges = outgoing.get(normalize(subject));
        return edges != null ? Collections.unmodifiableList(edges) : Collections.emptyList();
    }

    /**
     * Check if adding a new triple would contradict existing knowledge.
     * Returns description of contradiction, or null if no conflict.
     */
    public String detectContradiction(String subject, String predicate, String object) {
        // Check direct contradiction: "A replaces B" + "B replaces A"
        if ("replaces".equals(predicate)) {
            if (check(object, "replaces", subject)) {
                return "Contradiction: '" + object + " replaces " + subject + "' already exists. " +
                       "Cannot have both directions.";
            }
        }

        // Check cycle in "requires": A requires B requires A
        if ("requires".equals(predicate)) {
            List<String> chain = checkTransitive(object, "requires", subject);
            if (!chain.isEmpty()) {
                return "Circular dependency: " + subject + " requires " + object +
                       ", but " + String.join(" → ", chain) + " → " + subject;
            }
        }

        return null; // No contradiction
    }

    /**
     * Number of nodes in the graph.
     */
    public int nodeCount() {
        Set<String> nodes = new HashSet<>();
        nodes.addAll(outgoing.keySet());
        nodes.addAll(incoming.keySet());
        return nodes.size();
    }

    /**
     * Number of edges in the graph.
     */
    public int edgeCount() {
        return outgoing.values().stream().mapToInt(List::size).sum();
    }

    private String normalize(String s) {
        return s != null ? s.toLowerCase().trim() : "";
    }
}
