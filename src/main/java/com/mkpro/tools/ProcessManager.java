package com.mkpro.tools;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessManager {
    public static class JobInfo {
        public String id;
        public Process process;
        public String command;
        public long startTime;
        public File logFile;

        public JobInfo(String id, Process process, String command, File logFile) {
            this.id = id;
            this.process = process;
            this.command = command;
            this.startTime = System.currentTimeMillis();
            this.logFile = logFile;
        }
    }

    private static final Map<String, JobInfo> jobs = new ConcurrentHashMap<>();
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static String startJob(Process process, String command, File logFile) {
        String id = String.valueOf(counter.incrementAndGet());
        jobs.put(id, new JobInfo(id, process, command, logFile));
        return id;
    }

    public static String listJobs() {
        if (jobs.isEmpty()) {
            return "No active background jobs.";
        }
        StringBuilder sb = new StringBuilder("Active Jobs:\n");
        jobs.forEach((id, job) -> {
            boolean alive = job.process.isAlive();
            sb.append(String.format("[%s] (%s) %s%s\n", 
                id, 
                alive ? "Running" : "Finished", 
                job.command,
                job.logFile != null ? " (Log: " + job.logFile.getAbsolutePath() + ")" : ""
            ));
        });
        return sb.toString();
    }

    public static String killJob(String id) {
        JobInfo job = jobs.get(id);
        if (job == null) {
            return "Job not found: " + id;
        }
        if (job.process.isAlive()) {
            job.process.destroy();
            return "Killed job " + id;
        } else {
            return "Job " + id + " is not running.";
        }
    }
}
