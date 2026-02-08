package com.mkpro;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.IndexTreeList;
import org.mapdb.Serializer;
import com.mkpro.models.AgentStat;

import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CentralMemory {

    private final String dbPath;

    public CentralMemory() {
        String userHome = System.getProperty("user.home");
        File mkproDir = new File(userHome, ".mkpro");
        if (!mkproDir.exists()) {
            mkproDir.mkdirs();
        }
        this.dbPath = new File(mkproDir, "central_memory.db").getAbsolutePath();
    }

    private DB openDB() {
        return DBMaker.fileDB(dbPath)
                .transactionEnable()
                .make();
    }

    public void saveMemory(String projectPath, String content) {
        try (DB db = openDB()) {
            HTreeMap<String, String> projectMemories = db.hashMap("project_memories")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();

            // Append timestamp
            String timestampedContent = String.format("--- Saved: %s ---\n%s", Instant.now(), content);
            
            String existing = projectMemories.get(projectPath);
            if (existing != null) {
                timestampedContent = existing + "\n\n" + timestampedContent;
            }
            
            projectMemories.put(projectPath, timestampedContent);
            db.commit();
        }
    }

    public String getMemory(String projectPath) {
        try (DB db = openDB()) {
            HTreeMap<String, String> projectMemories = db.hashMap("project_memories")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
            return projectMemories.get(projectPath);
        }
    }
    
    public Map<String, String> getAllMemories() {
        try (DB db = openDB()) {
            HTreeMap<String, String> projectMemories = db.hashMap("project_memories")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
            Map<String, String> copy = new HashMap<>();
            projectMemories.forEach((k, v) -> copy.put((String)k, (String)v));
            return copy;
        }
    }

    public void saveAgentConfig(String projectPath, String teamName, String agentName, String provider, String modelName) {
        try (DB db = openDB()) {
            HTreeMap<String, String> configs = db.hashMap("agent_configs")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
            // Format Key: PROJECT:TEAM:AGENT -> Format Value: PROVIDER|MODEL
            // We hash the project path to keep keys reasonably short/safe, or just use it directly. 
            // Using direct string is simpler for debug.
            String key = projectPath + ":" + teamName + ":" + agentName;
            configs.put(key, provider + "|" + modelName);
            db.commit();
        }
    }

    public Map<String, String> getAgentConfigs(String projectPath, String teamName) {
        try (DB db = openDB()) {
            HTreeMap<String, String> configs = db.hashMap("agent_configs")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
            Map<String, String> teamConfigs = new HashMap<>();
            
            // 1. Try Project+Team specific configs
            String prefix = projectPath + ":" + teamName + ":";
            configs.forEach((k, v) -> {
                String key = (String) k;
                if (key.startsWith(prefix)) {
                    teamConfigs.put(key.substring(prefix.length()), (String) v);
                }
            });
            
            // 2. Fallback to Team-only configs (Legacy/Global defaults for that team)
            // This ensures if I switch to a new project but use "default" team, I still get my global preferences
            // unless overridden by project-specific settings.
            if (teamConfigs.isEmpty()) {
                String teamPrefix = teamName + ":";
                configs.forEach((k, v) -> {
                    String key = (String) k;
                    // Check it DOESN'T have a project path (contains only one colon or starts with team name and no other colon before it?)
                    // The legacy format was TEAM:AGENT. New is PROJECT:TEAM:AGENT.
                    // If key starts with teamName: and does NOT contain another : (or rather, is not part of a project path)
                    // Actually, legacy keys are just "TEAM:AGENT". New keys are "PROJECT:TEAM:AGENT".
                    // So we check if key starts with teamPrefix AND key does NOT start with ANY project path.
                    // A simple heuristic: Legacy keys have exactly one colon. New keys have at least two.
                    // Windows paths have colons (C:\...), so splitting by colon is tricky.
                    // Let's assume legacy keys were strictly "TEAM:AGENT".
                    
                    if (key.startsWith(teamPrefix) && !key.substring(teamPrefix.length()).contains(":")) {
                         // This is a global team config
                         String agent = key.substring(teamPrefix.length());
                         teamConfigs.putIfAbsent(agent, (String) v);
                    }
                });
            }
            
            return teamConfigs;
        }
    }

    // Legacy method for backward compatibility if needed, or just to list all
    public Map<String, String> getAllAgentConfigs() {
        try (DB db = openDB()) {
            HTreeMap<String, String> configs = db.hashMap("agent_configs")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
            Map<String, String> copy = new HashMap<>();
            configs.forEach((k, v) -> copy.put((String)k, (String)v));
            return copy;
        }
    }

    public void saveAgentStat(AgentStat stat) {
        try (DB db = openDB()) {
            IndexTreeList<AgentStat> stats = (IndexTreeList<AgentStat>) db.indexTreeList("agent_stats", Serializer.JAVA)
                    .createOrOpen();
            stats.add(stat);
            db.commit();
        }
    }

    public List<AgentStat> getAgentStats() {
        try (DB db = openDB()) {
            IndexTreeList<AgentStat> stats = (IndexTreeList<AgentStat>) db.indexTreeList("agent_stats", Serializer.JAVA)
                    .createOrOpen();
            // Return a copy to avoid concurrency issues after db close
            return new ArrayList<>(stats);
        }
    }

    // --- Ollama Configuration ---

    public void saveOllamaServers(List<String> servers) {
        try (DB db = openDB()) {
            HTreeMap<String, String> config = db.hashMap("ollama_config")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
            // Store as comma-separated string
            config.put("servers", String.join(",", servers));
            db.commit();
        }
    }

    public List<String> getOllamaServers() {
        try (DB db = openDB()) {
            HTreeMap<String, String> config = db.hashMap("ollama_config")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
            String servers = config.get("servers");
            if (servers == null || servers.isEmpty()) {
                // Default
                List<String> defaults = new ArrayList<>();
                defaults.add("http://localhost:11434");
                return defaults;
            }
            return new ArrayList<>(List.of(servers.split(",")));
        }
    }

    public void saveSelectedOllamaServer(String url) {
        try (DB db = openDB()) {
            HTreeMap<String, String> config = db.hashMap("ollama_config")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
            config.put("selected_server", url);
            db.commit();
        }
    }

    public String getSelectedOllamaServer() {
        try (DB db = openDB()) {
            HTreeMap<String, String> config = db.hashMap("ollama_config")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
            String selected = config.get("selected_server");
            return selected != null ? selected : "http://localhost:11434";
        }
    }

    public void addGoal(String projectPath, com.mkpro.models.Goal goal) {
        try (DB db = openDB()) {
            HTreeMap<String, ArrayList<com.mkpro.models.Goal>> projectGoals = db.hashMap("project_goals")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.JAVA)
                    .createOrOpen();
            
            ArrayList<com.mkpro.models.Goal> goals = projectGoals.get(projectPath);
            if (goals == null) {
                goals = new ArrayList<>();
            }
            goals.add(goal);
            projectGoals.put(projectPath, goals);
            db.commit();
        }
    }

    public List<com.mkpro.models.Goal> getGoals(String projectPath) {
        try (DB db = openDB()) {
            HTreeMap<String, ArrayList<com.mkpro.models.Goal>> projectGoals = db.hashMap("project_goals")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.JAVA)
                    .createOrOpen();
            
            ArrayList<com.mkpro.models.Goal> goals = projectGoals.get(projectPath);
            if (goals == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(goals);
        }
    }

    public void updateGoal(String projectPath, com.mkpro.models.Goal updatedGoal) {
        try (DB db = openDB()) {
            HTreeMap<String, ArrayList<com.mkpro.models.Goal>> projectGoals = db.hashMap("project_goals")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.JAVA)
                    .createOrOpen();
            
            ArrayList<com.mkpro.models.Goal> goals = projectGoals.get(projectPath);
            if (goals != null) {
                for (int i = 0; i < goals.size(); i++) {
                    if (goals.get(i).getId().equals(updatedGoal.getId())) {
                        goals.set(i, updatedGoal);
                        break;
                    }
                }
                projectGoals.put(projectPath, goals);
                db.commit();
            }
        }
    }
}