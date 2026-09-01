package com.mkpro.infra.ssh;

import com.jcraft.jsch.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SshSessionManager manages persistent SSH and SFTP connections using JSch.
 * Supports multi-session management, password and key authentication, remote command execution,
 * and SFTP file transfers.
 */
public class SshSessionManager {

    private static final SshSessionManager INSTANCE = new SshSessionManager();

    public static SshSessionManager getInstance() {
        return INSTANCE;
    }

    public static class SshSessionEntry {
        private final String alias;
        private final String host;
        private final int port;
        private final String username;
        private final Session session;
        private final Instant connectedAt;
        private volatile Instant lastUsedAt;

        public SshSessionEntry(String alias, String host, int port, String username, Session session) {
            this.alias = alias;
            this.host = host;
            this.port = port;
            this.username = username;
            this.session = session;
            this.connectedAt = Instant.now();
            this.lastUsedAt = Instant.now();
        }

        public String getAlias() { return alias; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getUsername() { return username; }
        public Session getSession() { return session; }
        public Instant getConnectedAt() { return connectedAt; }
        public Instant getLastUsedAt() { return lastUsedAt; }
        public void updateLastUsed() { this.lastUsedAt = Instant.now(); }

        public boolean isConnected() {
            return session != null && session.isConnected();
        }
    }

    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final long durationMs;

        public CommandResult(int exitCode, String stdout, String stderr, long durationMs) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.durationMs = durationMs;
        }

        public int getExitCode() { return exitCode; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return exitCode == 0; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Exit Code: ").append(exitCode).append(" (").append(durationMs).append(" ms)\n");
            if (stdout != null && !stdout.isBlank()) {
                sb.append("── STDOUT ──\n").append(stdout.stripTrailing()).append("\n");
            }
            if (stderr != null && !stderr.isBlank()) {
                sb.append("── STDERR ──\n").append(stderr.stripTrailing()).append("\n");
            }
            return sb.toString();
        }
    }

    private final Map<String, SshSessionEntry> sessions = new ConcurrentHashMap<>();
    private final JSch jsch = new JSch();

    public SshSessionManager() {
        // Disable strict host key checking by default for automated CLI agents
        JSch.setConfig("StrictHostKeyChecking", "no");
        JSch.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
    }

    /**
     * Connect to a remote host and store the active session.
     */
    public synchronized SshSessionEntry connect(String host, int port, String username,
                                                String password, String privateKeyPath,
                                                String privateKeyContent, String passphrase,
                                                AuthType authType, String alias) throws Exception {
        String effectiveAlias = (alias == null || alias.isBlank()) ? "default" : alias.trim();
        int effectivePort = (port <= 0) ? 22 : port;

        // Disconnect existing session with the same alias if any
        disconnect(effectiveAlias);

        AuthType resolvedAuth = (authType != null) ? authType : AuthType.PASSWORD;

        // Configure authentication
        if (resolvedAuth == AuthType.KEY_FILE && privateKeyPath != null && !privateKeyPath.isBlank()) {
            if (passphrase != null && !passphrase.isBlank()) {
                jsch.addIdentity(privateKeyPath, passphrase);
            } else {
                jsch.addIdentity(privateKeyPath);
            }
        } else if (resolvedAuth == AuthType.KEY_CONTENT && privateKeyContent != null && !privateKeyContent.isBlank()) {
            byte[] prvkey = privateKeyContent.getBytes(StandardCharsets.UTF_8);
            byte[] pass = (passphrase != null && !passphrase.isBlank()) ? passphrase.getBytes(StandardCharsets.UTF_8) : null;
            jsch.addIdentity("inline-key-" + effectiveAlias, prvkey, null, pass);
        }

        Session session = jsch.getSession(username, host, effectivePort);
        if (password != null && !password.isBlank()) {
            session.setPassword(password);
        }

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.setTimeout(15000); // 15s socket timeout
        session.connect(15000);

        SshSessionEntry entry = new SshSessionEntry(effectiveAlias, host, effectivePort, username, session);
        sessions.put(effectiveAlias, entry);
        return entry;
    }

    /**
     * Execute a shell command on an active SSH session.
     */
    public CommandResult executeCommand(String alias, String command, int timeoutSeconds) throws Exception {
        SshSessionEntry entry = getSessionEntry(alias);
        if (entry == null || !entry.isConnected()) {
            throw new IllegalStateException("No active SSH session found for alias: '" + (alias != null ? alias : "default") + "'. Please connect first.");
        }

        entry.updateLastUsed();
        Session session = entry.getSession();
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);

        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();
        channel.setOutputStream(stdoutStream);
        channel.setErrStream(stderrStream);

        long start = System.currentTimeMillis();
        channel.connect(5000);

        int timeoutMs = (timeoutSeconds <= 0 ? 30 : timeoutSeconds) * 1000;
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (!channel.isClosed()) {
            if (System.currentTimeMillis() > deadline) {
                channel.disconnect();
                throw new java.util.concurrent.TimeoutException("Command timed out after " + timeoutSeconds + " seconds: " + command);
            }
            Thread.sleep(50);
        }

        int exitCode = channel.getExitStatus();
        channel.disconnect();
        long duration = System.currentTimeMillis() - start;

        String stdout = stdoutStream.toString(StandardCharsets.UTF_8);
        String stderr = stderrStream.toString(StandardCharsets.UTF_8);

        return new CommandResult(exitCode, stdout, stderr, duration);
    }

    /**
     * Upload a local file to a remote destination via SFTP.
     */
    public String uploadFile(String alias, String localPathStr, String remotePathStr) throws Exception {
        SshSessionEntry entry = getSessionEntry(alias);
        if (entry == null || !entry.isConnected()) {
            throw new IllegalStateException("No active SSH session found for alias: '" + (alias != null ? alias : "default") + "'. Please connect first.");
        }

        Path localPath = Paths.get(localPathStr);
        if (!Files.exists(localPath)) {
            throw new FileNotFoundException("Local file does not exist: " + localPathStr);
        }

        entry.updateLastUsed();
        Session session = entry.getSession();
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(5000);

        try {
            // Ensure remote parent directories exist if needed
            String remoteDir = remotePathStr.contains("/") ? remotePathStr.substring(0, remotePathStr.lastIndexOf('/')) : "";
            if (!remoteDir.isBlank()) {
                ensureRemoteDir(sftp, remoteDir);
            }

            try (InputStream in = Files.newInputStream(localPath)) {
                sftp.put(in, remotePathStr, ChannelSftp.OVERWRITE);
            }
            long size = Files.size(localPath);
            return String.format("Successfully uploaded '%s' (%d bytes) to remote '%s'", localPathStr, size, remotePathStr);
        } finally {
            sftp.disconnect();
        }
    }

    /**
     * Download a remote file to a local destination via SFTP.
     */
    public String downloadFile(String alias, String remotePathStr, String localPathStr) throws Exception {
        SshSessionEntry entry = getSessionEntry(alias);
        if (entry == null || !entry.isConnected()) {
            throw new IllegalStateException("No active SSH session found for alias: '" + (alias != null ? alias : "default") + "'. Please connect first.");
        }

        entry.updateLastUsed();
        Session session = entry.getSession();
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(5000);

        try {
            Path localPath = Paths.get(localPathStr);
            if (localPath.getParent() != null) {
                Files.createDirectories(localPath.getParent());
            }

            try (OutputStream out = Files.newOutputStream(localPath)) {
                sftp.get(remotePathStr, out);
            }
            long size = Files.size(localPath);
            return String.format("Successfully downloaded remote '%s' to '%s' (%d bytes)", remotePathStr, localPathStr, size);
        } finally {
            sftp.disconnect();
        }
    }

    private void ensureRemoteDir(ChannelSftp sftp, String remoteDir) {
        String[] parts = remoteDir.split("/");
        StringBuilder path = new StringBuilder();
        if (remoteDir.startsWith("/")) {
            path.append("/");
        }
        for (String part : parts) {
            if (part.isBlank()) continue;
            path.append(part).append("/");
            try {
                sftp.cd(path.toString());
            } catch (SftpException e) {
                try {
                    sftp.mkdir(path.toString());
                } catch (SftpException ignored) {}
            }
        }
    }

    /**
     * Disconnect a session by alias.
     */
    public synchronized boolean disconnect(String alias) {
        String key = (alias == null || alias.isBlank()) ? "default" : alias.trim();
        SshSessionEntry entry = sessions.remove(key);
        if (entry != null && entry.getSession() != null) {
            try {
                if (entry.getSession().isConnected()) {
                    entry.getSession().disconnect();
                }
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    /**
     * Disconnect all active sessions.
     */
    public synchronized void disconnectAll() {
        for (String key : new ArrayList<>(sessions.keySet())) {
            disconnect(key);
        }
    }

    public SshSessionEntry getSessionEntry(String alias) {
        String key = (alias == null || alias.isBlank()) ? "default" : alias.trim();
        return sessions.get(key);
    }

    public List<SshSessionEntry> listSessions() {
        return new ArrayList<>(sessions.values());
    }
}