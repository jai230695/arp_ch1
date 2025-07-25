// File: src/main/java/com/arp/core/data/CSVParser.java
package com.arp_1.core.data;

import java.io.*;
import java.util.*;

public class CSVParser {

    public static List<Map<String, String>> parseCSV(String filePath) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Empty CSV file: " + filePath);
            }

            String[] headers = headerLine.split(",");
            // Trim whitespace from headers
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                Map<String, String> record = new HashMap<>();

                for (int i = 0; i < headers.length && i < values.length; i++) {
                    record.put(headers[i], values[i].trim());
                }

                records.add(record);
            }
        }

        return records;
    }

    public static Map<String, String> parseSingleRowCSV(String filePath) throws IOException {
        List<Map<String, String>> records = parseCSV(filePath);
        if (records.isEmpty()) {
            throw new IOException("No data found in CSV file: " + filePath);
        }
        return records.get(0);
    }

    public static void validateRequiredFields(Map<String, String> record, String... requiredFields) {
        for (String field : requiredFields) {
            if (!record.containsKey(field) || record.get(field).isEmpty()) {
                throw new IllegalArgumentException("Missing required field: " + field);
            }
        }
    }

    public static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) ||
                "1".equals(value) ||
                "yes".equalsIgnoreCase(value) ||
                "work".equalsIgnoreCase(value);
    }
}