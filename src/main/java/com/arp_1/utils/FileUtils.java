// File: src/main/java/com/arp/utils/FileUtils.java
package com.arp_1.utils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for file operations throughout the application
 */
public class FileUtils {

    /**
     * Check if a file exists
     */
    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    /**
     * Check if a directory exists
     */
    public static boolean directoryExists(String directoryPath) {
        return Files.exists(Paths.get(directoryPath)) && Files.isDirectory(Paths.get(directoryPath));
    }

    /**
     * Create directory if it doesn't exist
     */
    public static boolean createDirectoryIfNotExists(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                return true;
            }
            return false;
        } catch (IOException e) {
            LoggingUtils.logError("Failed to create directory: " + directoryPath, e);
            return false;
        }
    }

    /**
     * Read all lines from a file
     */
    public static List<String> readAllLines(String filePath) throws IOException {
        return Files.readAllLines(Paths.get(filePath));
    }

    /**
     * Write lines to a file
     */
    public static void writeLines(String filePath, List<String> lines) throws IOException {
        Files.write(Paths.get(filePath), lines);
    }

    /**
     * Append line to a file
     */
    public static void appendLine(String filePath, String line) throws IOException {
        Files.write(Paths.get(filePath), (line + System.lineSeparator()).getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Write string to file
     */
    public static void writeString(String filePath, String content) throws IOException {
        Files.write(Paths.get(filePath), content.getBytes());
    }

    /**
     * Read string from file
     */
    public static String readString(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    /**
     * Copy file from source to destination
     */
    public static void copyFile(String sourcePath, String destinationPath) throws IOException {
        Files.copy(Paths.get(sourcePath), Paths.get(destinationPath), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Delete file if it exists
     */
    public static boolean deleteFile(String filePath) {
        try {
            return Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            LoggingUtils.logError("Failed to delete file: " + filePath, e);
            return false;
        }
    }

    /**
     * Get file extension
     */
    public static String getFileExtension(String filePath) {
        String fileName = Paths.get(filePath).getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }

    /**
     * Get file name without extension
     */
    public static String getFileNameWithoutExtension(String filePath) {
        String fileName = Paths.get(filePath).getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
    }

    /**
     * List all files in directory with specific extension
     */
    public static List<Path> listFilesWithExtension(String directoryPath, String extension) {
        try {
            return Files.walk(Paths.get(directoryPath))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith("." + extension.toLowerCase()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LoggingUtils.logError("Failed to list files in directory: " + directoryPath, e);
            return new ArrayList<>();
        }
    }

    /**
     * Get file size in bytes
     */
    public static long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            LoggingUtils.logError("Failed to get file size: " + filePath, e);
            return -1;
        }
    }

    /**
     * Get timestamp when file was last modified
     */
    public static long getLastModifiedTime(String filePath) {
        try {
            return Files.getLastModifiedTime(Paths.get(filePath)).toMillis();
        } catch (IOException e) {
            LoggingUtils.logError("Failed to get last modified time: " + filePath, e);
            return -1;
        }
    }

    /**
     * Create backup of file with timestamp
     */
    public static String createBackup(String filePath) {
        try {
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupPath = filePath + ".backup." + timestamp;
            copyFile(filePath, backupPath);
            LoggingUtils.logInfo("Backup created: " + backupPath);
            return backupPath;
        } catch (IOException e) {
            LoggingUtils.logError("Failed to create backup for: " + filePath, e);
            return null;
        }
    }

    /**
     * Validate CSV file format
     */
    public static boolean isValidCSVFile(String filePath) {
        if (!fileExists(filePath)) {
            return false;
        }

        if (!"csv".equals(getFileExtension(filePath).toLowerCase())) {
            return false;
        }

        try {
            List<String> lines = readAllLines(filePath);
            if (lines.isEmpty()) {
                return false;
            }

            // Check if first line looks like a header
            String header = lines.get(0);
            return header.contains(",") && !header.trim().isEmpty();

        } catch (IOException e) {
            LoggingUtils.logError("Failed to validate CSV file: " + filePath, e);
            return false;
        }
    }

    /**
     * Create results directory structure
     */
    public static void createResultsDirectoryStructure(String baseResultsPath) {
        String[] directories = {
                baseResultsPath,
                baseResultsPath + "/deterministic",
                baseResultsPath + "/randomized",
                baseResultsPath + "/comparison",
                baseResultsPath + "/statistical"
        };

        for (String dir : directories) {
            createDirectoryIfNotExists(dir);
        }

        LoggingUtils.logInfo("Results directory structure created at: " + baseResultsPath);
    }

    /**
     * Write experiment results to CSV
     */
    public static void writeExperimentResultsCSV(String filePath, List<Map<String, Object>> results) {
        try {
            if (results.isEmpty()) {
                LoggingUtils.logWarning("No results to write to CSV");
                return;
            }

            List<String> lines = new ArrayList<>();

            // Create header from first result keys
            Map<String, Object> firstResult = results.get(0);
            String header = String.join(",", firstResult.keySet());
            lines.add(header);

            // Add data rows
            for (Map<String, Object> result : results) {
                String row = result.values().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
                lines.add(row);
            }

            writeLines(filePath, lines);
            LoggingUtils.logInfo("Experiment results written to: " + filePath);

        } catch (IOException e) {
            LoggingUtils.logError("Failed to write experiment results CSV: " + filePath, e);
        }
    }

    /**
     * Create unique filename with timestamp
     */
    public static String createTimestampedFilename(String baseName, String extension) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return baseName + "_" + timestamp + "." + extension;
    }

    /**
     * Clean up temporary files older than specified days
     */
    public static void cleanupTempFiles(String tempDirectory, int daysOld) {
        try {
            long cutoffTime = System.currentTimeMillis() - (daysOld * 24L * 60L * 60L * 1000L);

            Files.walk(Paths.get(tempDirectory))
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            LoggingUtils.logDebug("Cleaned up temp file: " + path);
                        } catch (IOException e) {
                            LoggingUtils.logWarning("Failed to delete temp file: " + path);
                        }
                    });

        } catch (IOException e) {
            LoggingUtils.logError("Failed to cleanup temp files in: " + tempDirectory, e);
        }
    }
}