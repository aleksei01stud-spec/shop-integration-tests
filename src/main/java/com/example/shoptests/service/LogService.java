package com.example.shoptests.service;

import java.util.*;

public class LogService {
    public enum Level { INFO, WARN, ERROR }

    public record LogEntry(Level level, String operation, long userId,
                           String result, long timestamp) {}

    private final List<LogEntry> entries = Collections.synchronizedList(new ArrayList<>());

    public void log(Level level, String operation, long userId, String result) {
        entries.add(new LogEntry(level, operation, userId, result, System.currentTimeMillis()));
    }

    public List<LogEntry> getEntries() { return List.copyOf(entries); }
    public void clear() { entries.clear(); }
}