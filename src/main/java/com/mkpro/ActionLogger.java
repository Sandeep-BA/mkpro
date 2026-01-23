package com.mkpro;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.IndexTreeList;
import org.mapdb.Serializer;

import java.io.Closeable;
import java.time.Instant;
import java.util.concurrent.ConcurrentMap;

public class ActionLogger implements Closeable {

    private final DB db;
    private final IndexTreeList<String> logs;

    public ActionLogger(String dbPath) {
        this.db = DBMaker.fileDB(dbPath)
                .transactionEnable()
                .make();
        this.logs = db.indexTreeList("action_logs", Serializer.STRING).createOrOpen();
    }

    public void log(String role, String content) {
        String entry = String.format("[%s] %s: %s", Instant.now(), role, content);
        logs.add(entry);
        db.commit();
    }

    public void printLogs() {
        System.out.println("----- Action Logs -----");
        for (String log : logs) {
            System.out.println(log);
        }
        System.out.println("-----------------------");
    }

    public java.util.List<String> getLogs() {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String log : logs) {
            result.add(log);
        }
        return result;
    }

    public java.util.List<String> getRecentLogs(int limit) {
        java.util.List<String> result = new java.util.ArrayList<>();
        int size = logs.size();
        int start = Math.max(0, size - limit);
        for (int i = start; i < size; i++) {
            result.add(logs.get(i));
        }
        return result;
    }

    @Override
    public void close() {
        if (!db.isClosed()) {
            db.close();
        }
    }
}
