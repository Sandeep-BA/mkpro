package com.mkpro;

import com.google.adk.memory.EmbeddingService;
import com.google.adk.memory.MemoryEntry;
import com.google.adk.memory.VectorStore;
import com.google.adk.memory.Vector;
import com.google.adk.memory.ZeroEmbeddingService;
import com.google.adk.memory.MapDBVectorStore;
import com.google.genai.types.Content;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class IndexingHelper {

    public static final String ANSI_RESET = "\u001b[0m";
    public static final String ANSI_BLUE = "\u001b[34m";

    public static EmbeddingService createEmbeddingService() {
        return new ZeroEmbeddingService(768);
    }

    public static MapDBVectorStore createVectorStore() {
        String projectName = Paths.get("").toAbsolutePath().getFileName().toString();
        // Sanitize project name
        projectName = projectName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String vectorDbPath = Paths.get(System.getProperty("user.home"), ".mkpro", "vectors", projectName + ".db").toString();
        
        File dbFile = new File(vectorDbPath);
        if (dbFile.getParentFile() != null) {
            dbFile.getParentFile().mkdirs();
        }
        
        return new MapDBVectorStore(vectorDbPath,projectName);
    }

    public static void indexCodebase(VectorStore vectorStore, EmbeddingService embeddingService) {
        System.out.println(ANSI_BLUE + "Indexing codebase..." + ANSI_RESET);
        try {
            Path startPath = Paths.get("").toAbsolutePath();
            AtomicInteger count = new AtomicInteger(0);
            Files.walk(startPath)
                .filter(p -> Files.isRegularFile(p))
                .filter(p -> {
                    String s = p.toString();
                    return !s.contains(".git") && !s.contains("target") && !s.contains("node_modules") && 
                           !s.endsWith(".db") && !s.endsWith(".class") && !s.endsWith(".jar") && !s.contains(".venv");
                })
                .forEach(p -> {
                    try {
                        if (Files.size(p) < 100000) { // Skip huge files
                            String content = "File: " + startPath.relativize(p).toString() + "\n\n" + Files.readString(p);
                            
                         
                            
                            double[] vector = embeddingService.generateEmbedding(content).blockingGet();
                            vectorStore.insertVector(new Vector(""+System.currentTimeMillis(), content, vector, new HashMap())); //Do better work for id generation.
                            
                            int c = count.incrementAndGet();
                            if (c % 10 == 0) System.out.print(".");
                        }
                    } catch (Exception e) {
                        // Ignore read errors
                    }
                });
            System.out.println("\n" + ANSI_BLUE + "Indexed " + count.get() + " files." + ANSI_RESET);
        } catch (IOException e) {
            System.err.println("Error indexing: " + e.getMessage());
        }
    }
}
