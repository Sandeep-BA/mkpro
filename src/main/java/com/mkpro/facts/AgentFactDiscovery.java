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

        // 1. Build the file tree
        GitIgnoreFilter gitIgnore = new GitIgnoreFilter(projectRoot);
        List<String> fileTree = buildFileTree(projectRoot, gitIgnore);

        if (fileTree.isEmpty()) {
            System.out.println("\u001b[33m  [Deep Discovery] No files found in project.\u001b[0m");
            return 0;
        }

        // 2. Ask LLM to pick the most important files
        List<String> selectedFiles = askLlmToPickFiles(fileTree);

        if (selectedFiles.isEmpty()) {
            System.out.println("\u001b[33m  [Deep Discovery] LLM selected no files for analysis.\u001b[0m");
            return 0;
        }

        System.out.println("\u001b[36m  [Deep Discovery] Analyzing " + selectedFiles.size() + " key file(s)...\u001b[0m");

        // 3. Analyze each selected file
        for (String relativePath : selectedFiles) {
            Path file = projectRoot.resolve(relativePath);
            if (!Files.exists(file) || !Files.isRegularFile(file)) continue;

            try {
                String content = readTruncated(file, MAX_FILE_SIZE);
                if (content == null || content.isBlank()) continue;

                System.out.println("\u001b[90m    Analyzing: " + relativePath + "\u001b[0m");
                analyzeFile(relativePath, content);
            } catch (Exception e) {
                // Skip problematic files
            }
        }

        return factsAdded;
    }

    /**
     * Build a file tree listing (respecting .gitignore), capped at 500 entries.
     */
    private List<String> buildFileTree(Path root, GitIgnoreFilter gitIgnore) {
        List<String> tree = new ArrayList<>();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 10, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (tree.size() >= 500) return FileVisitResult.TERMINATE;
                    if (attrs.size() > 500_000 || attrs.size() < 10) return FileVisitResult.CONTINUE;
                    if (gitIgnore.isIgnored(file)) return FileVisitResult.CONTINUE;

                    tree.add(root.relativize(file).toString().replace('\\', '/'));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!gitIgnore.shouldEnterDirectory(dir)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) { /* skip */ }
        return tree;
    }

    /**
     * Ask LLM to select the most important files from the tree.
     */
    private List<String> askLlmToPickFiles(List<String> fileTree) {
        String treeText = String.join("\n", fileTree);
        if (treeText.length() > 6000) {
            treeText = treeText.substring(0, 6000) + "\n... (truncated)";
        }

        String prompt = "Here is a project's file listing:\n\n" + treeText + "\n\n" +
            "Select the " + MAX_FILES_TO_ANALYZE + " most important files for understanding this project's " +
            "architecture, dependencies, technology relationships, configuration constraints, and any mathematical/scientific formulas.\n\n" +
            "Prioritize:\n" +
            "- README and documentation\n" +
            "- Dependency manifests (pom.xml, package.json, go.mod, etc.)\n" +
            "- Main entry points and core business logic\n" +
            "- Configuration files with limits/thresholds\n" +
            "- Infrastructure definitions (Docker, K8s, Terraform)\n\n" +
            "Respond with ONLY the file paths, one per line. No numbering, no explanation.";

        String response = llmCallback.apply(prompt);
        if (response == null || response.isBlank()) return Collections.emptyList();

        List<String> selected = new ArrayList<>();
        Set<String> fileTreeSet = new HashSet<>(fileTree);

        for (String line : response.split("\n")) {
            String path = line.trim().replaceAll("^[\\d.\\-*]+\\s*", ""); // Strip numbering/bullets
            path = path.replaceAll("^`|`$", ""); // Strip backticks
            if (path.isEmpty()) continue;

            // Match against actual file tree (exact or fuzzy)
            if (fileTreeSet.contains(path)) {
                selected.add(path);
            } else {
                // Try fuzzy match (LLM might format slightly differently)
                for (String actual : fileTree) {
                    if (actual.endsWith(path) || actual.replace('/', '\\').endsWith(path)) {
                        selected.add(actual);
                        break;
                    }
                }
            }

            if (selected.size() >= MAX_FILES_TO_ANALYZE) break;
        }

        return selected;
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

}
