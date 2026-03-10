package com.mkpro.models;

import java.io.Serializable;
import java.util.UUID;

public class McpServer implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum McpType {
        FIGMA, BROWSER, DATABASE, API, CUSTOM
    }

    private String id;
    private String name;
    private String url;
    private McpType type;
    private boolean enabled;
    private long createdAt;
    private long lastConnectedAt;

    public McpServer(String name, String url, McpType type) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.url = url;
        this.type = type;
        this.enabled = true;
        this.createdAt = System.currentTimeMillis();
        this.lastConnectedAt = 0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public McpType getType() { return type; }
    public boolean isEnabled() { return enabled; }
    public long getCreatedAt() { return createdAt; }
    public long getLastConnectedAt() { return lastConnectedAt; }

    public void setName(String name) { this.name = name; }
    public void setUrl(String url) { this.url = url; }
    public void setType(McpType type) { this.type = type; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setLastConnectedAt(long ts) { this.lastConnectedAt = ts; }

    @Override
    public String toString() {
        String status = enabled ? "ON " : "OFF";
        return String.format("[%s] %-12s %-6s %s  (%s)", status, name, type, url, id);
    }
}
