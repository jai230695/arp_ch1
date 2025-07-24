// File: src/main/java/com/arp/utils/ConfigurationManager.java
package com.arp_1.utils;

import java.io.*;
import java.util.*;

/**
 * Utility class for managing application configuration
 */
public class ConfigurationManager {

    private static final String CONFIG_DIR = "config/";
    private static final String DEFAULT_CONFIG_FILE = CONFIG_DIR + "application.properties";

    private static Properties globalProperties = new Properties();
    private static Map<String, Properties> configCache = new HashMap<>();

    static {
        loadDefaultConfiguration();
    }

    /**
     * Load default configuration from application.properties
     */
    private static void loadDefaultConfiguration() {
        try {
            loadConfiguration(DEFAULT_CONFIG_FILE);
            LoggingUtils.logInfo("Default configuration loaded");
        } catch (Exception e) {
            LoggingUtils.logWarning("Could not load default configuration: " + e.getMessage());
            setDefaultProperties();
        }
    }

    /**
     * Set default properties if configuration file is not available
     */
    private static void setDefaultProperties() {
        globalProperties.setProperty("logging.level", "INFO");
        globalProperties.setProperty("random.default_seed", "12345");
        globalProperties.setProperty("solver.max_iterations", "1000");
        globalProperties.setProperty("solver.time_limit_seconds", "300");
        globalProperties.setProperty("experiment.default_runs", "30");
        globalProperties.setProperty("output.results_directory", "results/");
        globalProperties.setProperty("output.backup_results", "true");

        LoggingUtils.logInfo("Default properties set");
    }

    /**
     * Load configuration from specific file
     */
    public static void loadConfiguration(String configFilePath) {
        try {
            if (!FileUtils.fileExists(configFilePath)) {
                LoggingUtils.logWarning("Configuration file not found: " + configFilePath);
                return;
            }

            Properties props = new Properties();
            try (InputStream input = new FileInputStream(configFilePath)) {
                props.load(input);
                globalProperties.putAll(props);
                configCache.put(configFilePath, props);
                LoggingUtils.logInfo("Configuration loaded from: " + configFilePath);
            }

        } catch (IOException e) {
            LoggingUtils.logError("Failed to load configuration: " + configFilePath, e);
        }
    }

    /**
     * Load multiple configuration files
     */
    public static void loadConfigurations(String... configFilePaths) {
        for (String configPath : configFilePaths) {
            loadConfiguration(configPath);
        }
    }

    /**
     * Load standard configuration files
     */
    public static void loadStandardConfigurations() {
        String[] standardConfigs = {
                CONFIG_DIR + "constraints.properties",
                CONFIG_DIR + "heuristics.properties",
                CONFIG_DIR + "random_seeds.properties",
                CONFIG_DIR + "logging.properties"
        };

        loadConfigurations(standardConfigs);
    }

    /**
     * Get string property
     */
    public static String getProperty(String key) {
        return globalProperties.getProperty(key);
    }

    /**
     * Get string property with default value
     */
    public static String getProperty(String key, String defaultValue) {
        return globalProperties.getProperty(key, defaultValue);
    }

    /**
     * Get integer property
     */
    public static int getIntProperty(String key, int defaultValue) {
        String value = globalProperties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LoggingUtils.logWarning("Invalid integer property " + key + ": " + value +
                    ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get double property
     */
    public static double getDoubleProperty(String key, double defaultValue) {
        String value = globalProperties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            LoggingUtils.logWarning("Invalid double property " + key + ": " + value +
                    ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get boolean property
     */
    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = globalProperties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        value = value.trim().toLowerCase();
        return "true".equals(value) || "yes".equals(value) || "1".equals(value);
    }

    /**
     * Get long property
     */
    public static long getLongProperty(String key, long defaultValue) {
        String value = globalProperties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LoggingUtils.logWarning("Invalid long property " + key + ": " + value +
                    ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get array property (comma-separated values)
     */
    public static String[] getArrayProperty(String key, String[] defaultValue) {
        String value = globalProperties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        return value.split(",");
    }

    /**
     * Get integer array property
     */
    public static int[] getIntArrayProperty(String key, int[] defaultValue) {
        String[] stringArray = getArrayProperty(key, null);
        if (stringArray == null) {
            return defaultValue;
        }

        try {
            int[] result = new int[stringArray.length];
            for (int i = 0; i < stringArray.length; i++) {
                result[i] = Integer.parseInt(stringArray[i].trim());
            }
            return result;
        } catch (NumberFormatException e) {
            LoggingUtils.logWarning("Invalid integer array property " + key +
                    ", using default");
            return defaultValue;
        }
    }

    /**
     * Get double array property
     */
    public static double[] getDoubleArrayProperty(String key, double[] defaultValue) {
        String[] stringArray = getArrayProperty(key, null);
        if (stringArray == null) {
            return defaultValue;
        }

        try {
            double[] result = new double[stringArray.length];
            for (int i = 0; i < stringArray.length; i++) {
                result[i] = Double.parseDouble(stringArray[i].trim());
            }
            return result;
        } catch (NumberFormatException e) {
            LoggingUtils.logWarning("Invalid double array property " + key +
                    ", using default");
            return defaultValue;
        }
    }

    /**
     * Set property
     */
    public static void setProperty(String key, String value) {
        globalProperties.setProperty(key, value);
    }

    /**
     * Set integer property
     */
    public static void setIntProperty(String key, int value) {
        globalProperties.setProperty(key, String.valueOf(value));
    }

    /**
     * Set double property
     */
    public static void setDoubleProperty(String key, double value) {
        globalProperties.setProperty(key, String.valueOf(value));
    }

    /**
     * Set boolean property
     */
    public static void setBooleanProperty(String key, boolean value) {
        globalProperties.setProperty(key, String.valueOf(value));
    }

    /**
     * Set long property
     */
    public static void setLongProperty(String key, long value) {
        globalProperties.setProperty(key, String.valueOf(value));
    }

    /**
     * Save configuration to file
     */
    public static void saveConfiguration(String configFilePath) {
        try {
            FileUtils.createDirectoryIfNotExists(CONFIG_DIR);

            try (OutputStream output = new FileOutputStream(configFilePath)) {
                globalProperties.store(output, "Generated configuration - " +
                        java.time.LocalDateTime.now().toString());
                LoggingUtils.logInfo("Configuration saved to: " + configFilePath);
            }

        } catch (IOException e) {
            LoggingUtils.logError("Failed to save configuration: " + configFilePath, e);
        }
    }

    /**
     * Get all properties as Map
     */
    public static Map<String, String> getAllProperties() {
        Map<String, String> map = new HashMap<>();
        for (String key : globalProperties.stringPropertyNames()) {
            map.put(key, globalProperties.getProperty(key));
        }
        return map;
    }

    /**
     * Get properties with specific prefix
     */
    public static Map<String, String> getPropertiesWithPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();
        for (String key : globalProperties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                result.put(key, globalProperties.getProperty(key));
            }
        }
        return result;
    }

    /**
     * Check if property exists
     */
    public static boolean hasProperty(String key) {
        return globalProperties.containsKey(key);
    }

    /**
     * Remove property
     */
    public static void removeProperty(String key) {
        globalProperties.remove(key);
    }

    /**
     * Clear all properties
     */
    public static void clearAllProperties() {
        globalProperties.clear();
        configCache.clear();
        LoggingUtils.logInfo("All configuration properties cleared");
    }

    /**
     * Reload configuration from all cached files
     */
    public static void reloadConfiguration() {
        globalProperties.clear();
        Set<String> configFiles = new HashSet<>(configCache.keySet());
        configCache.clear();

        setDefaultProperties();

        for (String configFile : configFiles) {
            loadConfiguration(configFile);
        }

        LoggingUtils.logInfo("Configuration reloaded");
    }

    /**
     * Get solver configuration as Map
     */
    public static Map<String, Object> getSolverConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_iterations", getIntProperty("solver.max_iterations", 1000));
        config.put("time_limit_seconds", getIntProperty("solver.time_limit_seconds", 300));
        config.put("random_seed", getLongProperty("random.default_seed", 12345L));
        config.put("enable_logging", getBooleanProperty("solver.enable_logging", true));
        return config;
    }

    /**
     * Get experiment configuration as Map
     */
    public static Map<String, Object> getExperimentConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("default_runs", getIntProperty("experiment.default_runs", 30));
        config.put("results_directory", getProperty("output.results_directory", "results/"));
        config.put("backup_results", getBooleanProperty("output.backup_results", true));
        config.put("parallel_execution", getBooleanProperty("experiment.parallel_execution", true));
        config.put("statistical_confidence", getDoubleProperty("experiment.statistical_confidence", 0.95));
        return config;
    }

    /**
     * Get constraint weights configuration
     */
    public static Map<String, Double> getConstraintWeights() {
        Map<String, Double> weights = new HashMap<>();

        // Soft constraint default weights
        weights.put("SC1", getDoubleProperty("constraints.sc1_weight", 10.0)); // Rest day compliance
        weights.put("SC2", getDoubleProperty("constraints.sc2_weight", 5.0)); // No call requests
        weights.put("SC3", getDoubleProperty("constraints.sc3_weight", 30.0)); // Shift requests
        weights.put("SC4", getDoubleProperty("constraints.sc4_weight", 8.0)); // Preferred pairings
        weights.put("SC5", getDoubleProperty("constraints.sc5_weight", 10.0)); // Fair workload
        weights.put("SC6", getDoubleProperty("constraints.sc6_weight", 10.0)); // Fair weekend
        weights.put("SC7", getDoubleProperty("constraints.sc7_weight", 3.0)); // Fair pre-holiday
        weights.put("SC8", getDoubleProperty("constraints.sc8_weight", 8.0)); // Preferences
        weights.put("SC9", getDoubleProperty("constraints.sc9_weight", 8.0)); // Consecutive days
        weights.put("SC10", getDoubleProperty("constraints.sc10_weight", 8.0)); // Undesired combinations

        return weights;
    }

    /**
     * Print current configuration summary
     */
    public static void printConfigurationSummary() {
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("CONFIGURATION SUMMARY");
        LoggingUtils.logSeparator();

        Map<String, String> allProps = getAllProperties();
        List<String> sortedKeys = new ArrayList<>(allProps.keySet());
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            LoggingUtils.logInfo(String.format("  %-30s: %s", key, allProps.get(key)));
        }

        LoggingUtils.logSeparator();
    }
}