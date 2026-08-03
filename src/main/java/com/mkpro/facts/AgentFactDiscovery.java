package com.mkpro.facts;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;

/**
 * Agent-assisted deep fact discovery. Uses LLM to analyze key project files
 * and extract complex relationships, architectural patterns, and formulas
 * that regex can't detect.
 *
 * Triggered by: /index --deep
 * Analyzes: README, main configs, core classes (top 10 by importance)
 */
public class AgentFactDiscovery {

    private static final double AGENT_FACT_CONFIDENCE = 0.85;
    private static final int MAX_FILES_TO_ANALYZE = 10;
    private static final int MAX_FILE_SIZE = 8000; // chars sent to LLM

    private final FactEngine factEngine;
    private final Function<String, String> llmCallback;
    private int factsAdded = 0;

    public AgentFactDiscovery(FactEngine factEngine, Function<String, String> llmCallback) {
        this.factEngine = factEngine;
        this.llmCallback = llmCallback;
    }

    /**
     * Run deep analysis on key project files.
     * @return number of facts discovered
     */
    public int analyze(Path projectRoot) {
        factsAdded = 0;

        if (llmCallback == null || factEngine == null) return 0;

        // 1. Pick the most important files to analyze
        List<Path> keyFiles = selectKeyFiles(projectRoot);

        System.out.println("\u001b[36m  [Deep Discovery] Analyzing " + keyFiles.size() + " key file(s)...\u001b[0m");

        // 2. Analyze each file
        for (Path file : keyFiles) {
            try {
                String content = readTruncated(file, MAX_FILE_SIZE);
                if (content == null || content.isBlank()) continue;

                String relativePath = projectRoot.relativize(file).toString();
                System.out.println("\u001b[90m    Analyzing: " + relativePath + "\u001b[0m");

                analyzeFile(relativePath, content);
            } catch (Exception e) {
                // Skip problematic files
            }
        }

        return factsAdded;
    }

    /**
     * Select the most important files for analysis.
     * Priority: README > main config > entry points > core classes
     */
    private List<Path> selectKeyFiles(Path root) {
        List<ScoredFile> candidates = new ArrayList<>();
        GitIgnoreFilter gitIgnore = new GitIgnoreFilter(root);

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 8, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.size() > 200_000 || attrs.size() < 50) return FileVisitResult.CONTINUE;
                    if (gitIgnore.isIgnored(file)) return FileVisitResult.CONTINUE;

                    String name = file.getFileName().toString().toLowerCase();
                    String relative = root.relativize(file).toString().toLowerCase().replace('\\', '/');
                    int score = scoreFile(name, relative);

                    if (score > 0) {
                        candidates.add(new ScoredFile(file, score));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!gitIgnore.shouldEnterDirectory(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) { /* skip */ }

        // Sort by score descending, take top N
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));
        List<Path> result = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_FILES_TO_ANALYZE, candidates.size()); i++) {
            result.add(candidates.get(i).path);
        }
        return result;
    }

    private int scoreFile(String name, String relativePath) {
        // README files — highest value
        if (name.startsWith("readme")) return 100;

        // Architecture/design docs
        if (name.contains("architecture") || name.contains("design")) return 90;

        // Main entry points
        if (name.equals("main.java") || name.equals("app.java") || name.equals("application.java")) return 85;
        if (name.equals("main.py") || name.equals("app.py")) return 85;
        if (name.equals("index.ts") || name.equals("index.js") || name.equals("main.ts")) return 85;

        // Config files
        if (name.equals("application.yaml") || name.equals("application.yml")) return 80;
        if (name.equals("application.properties")) return 80;
        if (name.equals("docker-compose.yaml") || name.equals("docker-compose.yml")) return 78;
        if (name.equals("dockerfile")) return 75;

        // Infrastructure
        if (name.endsWith(".tf")) return 70; // Terraform
        if (name.contains("kubernetes") || name.contains("k8s")) return 70;

        // Core source files (in src/main not test)
        if (relativePath.contains("src/main") && !relativePath.contains("test")) {
            if (name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".ts")) {
                // Service/Controller/Repository classes
                if (name.contains("service") || name.contains("controller") || name.contains("repository")) return 60;
                if (name.contains("config") || name.contains("module")) return 55;
            }
        }

        // Python/JS project roots
        if (name.equals("setup.py") || name.equals("pyproject.toml")) return 65;

        return 0; // Not important enough
    }

    private void analyzeFile(String relativePath, String content) {
        String prompt = "Analyze this source file and extract structured facts.\n\n" +
            "File: " + relativePath + "\n" +
            "```\n" + content + "\n```\n\n" +
            "Extract:\n" +
            "1. Technology relationships (what requires/uses/replaces what)\n" +
            "2. Mathematical formulas or computation logic\n" +
            "3. Architectural patterns and constraints\n" +
            "4. Configuration limits and thresholds\n\n" +
            "Format EACH finding as one of:\n" +
            "FACT: subject | predicate | object\n" +
            "FORMULA: name | expression | keyword1,keyword2\n" +
            "CONSTRAINT: name | condition | source\n\n" +
            "Predicates: requires, uses, replaces, configures, provides, implements, extends\n\n" +
            "Example:\n" +
            "FACT: UserService | requires | Redis\n" +
            "FORMULA: retry_delay | base_delay × 2^attempt | retry,backoff,exponential\n" +
            "CONSTRAINT: max_connections | <= 100 | application.yaml\n\n" +
            "Output up to 10 findings. If nothing meaningful, output: NONE";

        String response = llmCallback.apply(prompt);
        if (response == null || response.isBlank() || "NONE".equals(response.trim())) return;

        parseAndAddFindings(response, relativePath);
    }

    private void parseAndAddFindings(String response, String sourceFile) {
        for (String line : response.split("\n")) {
            line = line.trim();

            if (line.startsWith("FACT:")) {
                String content = line.substring(5).trim();
                String[] parts = content.split("\\|");
                if (parts.length == 3) {
                    String subject = parts[0].trim();
                    String predicate = parts[1].trim().toLowerCase();
                    String object = parts[2].trim();
                    if (subject.length() >= 2 && object.length() >= 2 && isValidPredicate(predicate)) {
                        factEngine.addRelationship(subject, predicate, object, "project:" + sourceFile, AGENT_FACT_CONFIDENCE);
                        factsAdded++;
                    }
                }
            } else if (line.startsWith("FORMULA:")) {
                String content = line.substring(8).trim();
                String[] parts = content.split("\\|");
                if (parts.length >= 2) {
                    String name = parts[0].trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
                    String formula = parts[1].trim();
                    List<String> keywords = new ArrayList<>();
                    if (parts.length >= 3) {
                        for (String kw : parts[2].trim().split(",")) {
                            kw = kw.trim().toLowerCase();
                            if (!kw.isEmpty()) keywords.add(kw);
                        }
                    }
                    if (name.length() >= 2 && formula.length() >= 3) {
                        MathFact fact = new MathFact();
                        fact.setKey("project." + name);
                        fact.setFormula(formula);
                        fact.setKeywords(keywords);
                        fact.setScript(null); // Agent-discovered, no auto-generated script
                        factEngine.getStore().addMathFact(fact);
                        factsAdded++;
                    }
                }
            } else if (line.startsWith("CONSTRAINT:")) {
                String content = line.substring(11).trim();
                String[] parts = content.split("\\|");
                if (parts.length >= 2) {
                    String name = parts[0].trim();
                    String condition = parts[1].trim();
                    String source = parts.length >= 3 ? parts[2].trim() : sourceFile;
                    factEngine.addRelationship("project", "constraint",
                        name + " " + condition, "project:" + source, AGENT_FACT_CONFIDENCE);
                    factsAdded++;
                }
            }
        }
    }

    private boolean isValidPredicate(String predicate) {
        return switch (predicate) {
            case "requires", "replaces", "extends", "part_of", "configures",
                 "alternative_to", "uses", "provides", "type", "manages",
                 "implements", "includes", "used_for", "prevents" -> true;
            default -> false;
        };
    }

    private String readTruncated(Path file, int maxChars) throws IOException {
        String content = Files.readString(file);
        if (content.length() > maxChars) {
            return content.substring(0, maxChars) + "\n... [truncated]";
        }
        return content;
    }

    private record ScoredFile(Path path, int score) {}
}
