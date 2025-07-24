// File: src/main/java/com/arp/utils/LoggingUtils.java
package com.arp_1.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility class for logging throughout the application
 */
public class LoggingUtils {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String LOG_PREFIX = "[ARP] ";

    // Log levels
    public enum LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }

    private static LogLevel currentLogLevel = LogLevel.INFO;

    /**
     * Set the current log level
     */
    public static void setLogLevel(LogLevel level) {
        currentLogLevel = level;
    }

    /**
     * Log debug message
     */
    public static void logDebug(String message) {
        if (shouldLog(LogLevel.DEBUG)) {
            System.out.println(formatMessage(LogLevel.DEBUG, message));
        }
    }

    /**
     * Log info message
     */
    public static void logInfo(String message) {
        if (shouldLog(LogLevel.INFO)) {
            System.out.println(formatMessage(LogLevel.INFO, message));
        }
    }

    /**
     * Log warning message
     */
    public static void logWarning(String message) {
        if (shouldLog(LogLevel.WARNING)) {
            System.err.println(formatMessage(LogLevel.WARNING, message));
        }
    }

    /**
     * Log error message
     */
    public static void logError(String message) {
        if (shouldLog(LogLevel.ERROR)) {
            System.err.println(formatMessage(LogLevel.ERROR, message));
        }
    }

    /**
     * Log error with exception
     */
    public static void logError(String message, Throwable throwable) {
        if (shouldLog(LogLevel.ERROR)) {
            System.err.println(formatMessage(LogLevel.ERROR, message));
            throwable.printStackTrace();
        }
    }

    /**
     * Log assignment details
     */
    public static void logAssignment(String workstationId, int dayNumber,
            List<String> anaesthetistIds, String method) {
        if (shouldLog(LogLevel.DEBUG)) {
            StringBuilder sb = new StringBuilder();
            sb.append("ASSIGNMENT: ").append(workstationId)
                    .append(" on day ").append(dayNumber)
                    .append(" -> [").append(String.join(", ", anaesthetistIds))
                    .append("] via ").append(method);
            logDebug(sb.toString());
        }
    }

    /**
     * Log solution statistics
     */
    public static void logSolutionStats(String phase, double objectiveValue,
            int hardViolations, int softViolations,
            long computationTime) {
        String message = String.format(
                "SOLUTION_STATS [%s]: Objective=%.2f, Hard=%d, Soft=%d, Time=%dms",
                phase, objectiveValue, hardViolations, softViolations, computationTime);
        logInfo(message);
    }

    /**
     * Log progress with percentage
     */
    public static void logProgress(String operation, int current, int total) {
        if (shouldLog(LogLevel.INFO) && total > 0) {
            double percentage = (double) current / total * 100;
            String message = String.format("PROGRESS [%s]: %d/%d (%.1f%%)",
                    operation, current, total, percentage);
            logInfo(message);
        }
    }

    /**
     * Log experiment results
     */
    public static void logExperimentResult(String configuration, int runNumber,
            double objectiveValue, boolean feasible,
            long computationTime) {
        String message = String.format(
                "EXPERIMENT [%s] Run %d: Obj=%.2f, Feasible=%s, Time=%dms",
                configuration, runNumber, objectiveValue, feasible, computationTime);
        logInfo(message);
    }

    /**
     * Log statistical summary
     */
    public static void logStatistics(String title, double mean, double stdDev,
            double min, double max, int sampleSize) {
        logInfo("STATISTICS [" + title + "]:");
        logInfo(String.format("  Mean: %.3f, StdDev: %.3f", mean, stdDev));
        logInfo(String.format("  Min: %.3f, Max: %.3f, N: %d", min, max, sampleSize));
    }

    /**
     * Create a separator line for logs
     */
    public static void logSeparator() {
        logInfo("=" + "=".repeat(60));
    }

    /**
     * Create a section header
     */
    public static void logSectionHeader(String sectionName) {
        logSeparator();
        logInfo("  " + sectionName.toUpperCase());
        logSeparator();
    }

    // Private helper methods
    private static boolean shouldLog(LogLevel level) {
        return level.ordinal() >= currentLogLevel.ordinal();
    }

    private static String formatMessage(LogLevel level, String message) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return String.format("%s%s [%s] %s",
                LOG_PREFIX, timestamp, level.name(), message);
    }
}