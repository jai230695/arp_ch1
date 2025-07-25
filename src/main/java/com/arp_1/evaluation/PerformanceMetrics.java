// File: src/main/java/com/arp_1/evaluation/PerformanceMetrics.java
package com.arp_1.evaluation;

import com.arp_1.core.models.Solution;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.utils.LoggingUtils;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Performance metrics calculator and analyzer with corrected MILP benchmarks
 */
public class PerformanceMetrics {

    // Metric storage
    private Map<String, Double> metrics;
    private Map<String, List<Double>> distributionMetrics;
    private List<Solution> solutions;
    private ProblemInstance instance;
    private long calculationTime;

    // CORRECTED MILP benchmarks based on actual MILP MODEL results
    private static final Map<String, Double> MILP_BENCHMARKS = new HashMap<>();
    static {
        // Month 1: SC1(20) + SC2(10) + SC3(30) + SC4(16) + SC5(20) + SC6(90) + SC7(15)
        // + SC8(0) + SC9(0) + SC10(0) = 201
        MILP_BENCHMARKS.put("month1", 201.0);
        MILP_BENCHMARKS.put("month_1", 201.0);

        // Month 2: SC1(150) + SC2(10) + SC3(30) + SC4(24) + SC5(430) + SC6(220) +
        // SC7(45) + SC8(32) + SC9(16) + SC10(40) = 997
        MILP_BENCHMARKS.put("month2", 997.0);
        MILP_BENCHMARKS.put("month_2", 997.0);

        // Month 3: SC1(150) + SC2(10) + SC3(30) + SC4(24) + SC5(730) + SC6(230) +
        // SC7(66) + SC8(32) + SC9(16) + SC10(40) = 1328
        MILP_BENCHMARKS.put("month3", 1328.0);
        MILP_BENCHMARKS.put("month_3", 1328.0);

        // Default fallback
        MILP_BENCHMARKS.put("default", 201.0);
    }

    public PerformanceMetrics() {
        this.metrics = new HashMap<>();
        this.distributionMetrics = new HashMap<>();
        this.solutions = new ArrayList<>();
    }

    /**
     * Calculate comprehensive metrics for a list of solutions
     */
    public void calculateMetrics(List<Solution> solutions, ProblemInstance instance) {
        long startTime = System.currentTimeMillis();

        this.solutions = new ArrayList<>(solutions);
        this.instance = instance;
        this.metrics.clear();
        this.distributionMetrics.clear();

        LoggingUtils.logInfo("Calculating performance metrics for " + solutions.size() + " solutions");

        if (solutions.isEmpty()) {
            LoggingUtils.logWarning("No solutions provided for metrics calculation");
            return;
        }

        // Basic objective metrics
        calculateObjectiveMetrics(solutions);

        // Feasibility metrics
        calculateFeasibilityMetrics(solutions);

        // Computation time metrics
        calculateComputationTimeMetrics(solutions);

        // Constraint violation metrics
        calculateConstraintViolationMetrics(solutions);

        // Quality distribution metrics
        calculateQualityDistributionMetrics(solutions);

        // Robustness metrics
        calculateRobustnessMetrics(solutions);

        // Comparative metrics (with corrected MILP benchmark)
        calculateComparativeMetrics(solutions, instance);

        this.calculationTime = System.currentTimeMillis() - startTime;

        LoggingUtils.logInfo("Performance metrics calculation completed in " + calculationTime + "ms");
    }

    /**
     * Get MILP benchmark for the given problem instance
     */
    public static double getMilpBenchmark(ProblemInstance instance) {
        String instanceName = instance.toString().toLowerCase();

        // Try to extract month identifier from instance
        for (Map.Entry<String, Double> entry : MILP_BENCHMARKS.entrySet()) {
            if (instanceName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Try alternative patterns
        if (instanceName.contains("1")) {
            return MILP_BENCHMARKS.get("month1");
        } else if (instanceName.contains("2")) {
            return MILP_BENCHMARKS.get("month2");
        } else if (instanceName.contains("3")) {
            return MILP_BENCHMARKS.get("month3");
        }

        // Default to month 1 if cannot determine
        LoggingUtils
                .logWarning("Could not determine month from instance: " + instanceName + ". Using Month 1 benchmark.");
        return MILP_BENCHMARKS.get("month1");
    }

    /**
     * Get MILP benchmark by month number
     */
    public static double getMilpBenchmark(int month) {
        switch (month) {
            case 1:
                return MILP_BENCHMARKS.get("month1");
            case 2:
                return MILP_BENCHMARKS.get("month2");
            case 3:
                return MILP_BENCHMARKS.get("month3");
            default:
                LoggingUtils.logWarning("Unknown month: " + month + ". Using Month 1 benchmark.");
                return MILP_BENCHMARKS.get("month1");
        }
    }

    private void calculateObjectiveMetrics(List<Solution> solutions) {
        List<Double> objectives = solutions.stream()
                .map(Solution::getObjectiveValue)
                .filter(obj -> obj < Double.MAX_VALUE) // Filter out failed solutions
                .collect(Collectors.toList());

        if (objectives.isEmpty()) {
            LoggingUtils.logWarning("No valid objective values found");
            return;
        }

        // Basic statistics
        double mean = objectives.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = objectives.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = objectives.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        double variance = objectives.stream()
                .mapToDouble(obj -> Math.pow(obj - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        // Store metrics
        metrics.put("objective_mean", mean);
        metrics.put("objective_min", min);
        metrics.put("objective_max", max);
        metrics.put("objective_std_dev", stdDev);
        metrics.put("objective_range", max - min);

        // Coefficient of variation
        if (mean > 0) {
            metrics.put("objective_cv", stdDev / mean);
        }

        // Percentiles
        Collections.sort(objectives);
        int n = objectives.size();
        metrics.put("objective_median", getPercentile(objectives, 50));
        metrics.put("objective_q1", getPercentile(objectives, 25));
        metrics.put("objective_q3", getPercentile(objectives, 75));

        distributionMetrics.put("objectives", new ArrayList<>(objectives));

        LoggingUtils.logDebug("Objective metrics calculated: mean=" + String.format("%.2f", mean) +
                ", std=" + String.format("%.2f", stdDev));
    }

    private void calculateFeasibilityMetrics(List<Solution> solutions) {
        long feasibleCount = solutions.stream()
                .mapToLong(solution -> solution.isFeasible() ? 1 : 0)
                .sum();

        double feasibilityRate = (double) feasibleCount / solutions.size();

        // Hard constraint violation analysis
        List<Integer> hardViolations = solutions.stream()
                .map(Solution::getHardConstraintViolations)
                .collect(Collectors.toList());

        double avgHardViolations = hardViolations.stream()
                .mapToInt(Integer::intValue)
                .average().orElse(0.0);

        int maxHardViolations = hardViolations.stream()
                .mapToInt(Integer::intValue)
                .max().orElse(0);

        // Soft constraint violation analysis
        List<Integer> softViolations = solutions.stream()
                .map(Solution::getSoftConstraintViolations)
                .collect(Collectors.toList());

        double avgSoftViolations = softViolations.stream()
                .mapToInt(Integer::intValue)
                .average().orElse(0.0);

        // Store metrics
        metrics.put("feasibility_rate", feasibilityRate);
        metrics.put("feasible_solutions", (double) feasibleCount);
        metrics.put("infeasible_solutions", (double) (solutions.size() - feasibleCount));
        metrics.put("avg_hard_violations", avgHardViolations);
        metrics.put("max_hard_violations", (double) maxHardViolations);
        metrics.put("avg_soft_violations", avgSoftViolations);

        distributionMetrics.put("hard_violations", hardViolations.stream()
                .map(Integer::doubleValue).collect(Collectors.toList()));
        distributionMetrics.put("soft_violations", softViolations.stream()
                .map(Integer::doubleValue).collect(Collectors.toList()));

        LoggingUtils.logDebug("Feasibility metrics calculated: rate=" +
                String.format("%.1f%%", feasibilityRate * 100));
    }

    private void calculateComputationTimeMetrics(List<Solution> solutions) {
        List<Long> computationTimes = solutions.stream()
                .map(Solution::getComputationTime)
                .filter(time -> time > 0)
                .collect(Collectors.toList());

        if (computationTimes.isEmpty()) {
            LoggingUtils.logWarning("No computation time data available");
            return;
        }

        double avgTime = computationTimes.stream()
                .mapToLong(Long::longValue)
                .average().orElse(0.0);

        long minTime = computationTimes.stream()
                .mapToLong(Long::longValue)
                .min().orElse(0L);

        long maxTime = computationTimes.stream()
                .mapToLong(Long::longValue)
                .max().orElse(0L);

        double totalTime = computationTimes.stream()
                .mapToLong(Long::longValue)
                .sum();

        // Store metrics
        metrics.put("avg_computation_time", avgTime);
        metrics.put("min_computation_time", (double) minTime);
        metrics.put("max_computation_time", (double) maxTime);
        metrics.put("total_computation_time", totalTime);

        distributionMetrics.put("computation_times", computationTimes.stream()
                .map(Long::doubleValue).collect(Collectors.toList()));

        LoggingUtils.logDebug("Computation time metrics calculated: avg=" +
                String.format("%.1f ms", avgTime));
    }

    private void calculateConstraintViolationMetrics(List<Solution> solutions) {
        Map<String, List<Integer>> constraintViolationsByType = new HashMap<>();

        // Collect violations by constraint type
        for (Solution solution : solutions) {
            Map<String, Integer> violations = solution.getConstraintViolations();

            for (Map.Entry<String, Integer> entry : violations.entrySet()) {
                String constraintType = entry.getKey();
                int violationCount = entry.getValue();

                constraintViolationsByType.computeIfAbsent(constraintType, k -> new ArrayList<>())
                        .add(violationCount);
            }
        }

        // Calculate statistics for each constraint type
        for (Map.Entry<String, List<Integer>> entry : constraintViolationsByType.entrySet()) {
            String constraintType = entry.getKey();
            List<Integer> violations = entry.getValue();

            double avgViolations = violations.stream()
                    .mapToInt(Integer::intValue)
                    .average().orElse(0.0);

            int maxViolations = violations.stream()
                    .mapToInt(Integer::intValue)
                    .max().orElse(0);

            long zeroViolations = violations.stream()
                    .mapToLong(v -> v == 0 ? 1 : 0)
                    .sum();

            double satisfactionRate = (double) zeroViolations / violations.size();

            metrics.put("avg_" + constraintType.toLowerCase(), avgViolations);
            metrics.put("max_" + constraintType.toLowerCase(), (double) maxViolations);
            metrics.put("satisfaction_rate_" + constraintType.toLowerCase(), satisfactionRate);
        }

        LoggingUtils.logDebug("Constraint violation metrics calculated for " +
                constraintViolationsByType.size() + " constraint types");
    }

    private void calculateQualityDistributionMetrics(List<Solution> solutions) {
        List<Double> feasibleObjectives = solutions.stream()
                .filter(Solution::isFeasible)
                .map(Solution::getObjectiveValue)
                .filter(obj -> obj < Double.MAX_VALUE)
                .collect(Collectors.toList());

        if (feasibleObjectives.isEmpty()) {
            LoggingUtils.logWarning("No feasible solutions for quality distribution analysis");
            return;
        }

        Collections.sort(feasibleObjectives);

        // Quality tiers (best 25%, middle 50%, worst 25%)
        int n = feasibleObjectives.size();
        int tier1Size = n / 4;
        int tier3Start = 3 * n / 4;

        double tier1Avg = feasibleObjectives.subList(0, Math.max(1, tier1Size)).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);

        double tier3Avg = feasibleObjectives.subList(Math.min(tier3Start, n - 1), n).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);

        metrics.put("best_quarter_avg", tier1Avg);
        metrics.put("worst_quarter_avg", tier3Avg);
        metrics.put("quality_spread", tier3Avg - tier1Avg);

        distributionMetrics.put("feasible_objectives", new ArrayList<>(feasibleObjectives));

        LoggingUtils.logDebug("Quality distribution metrics calculated");
    }

    private void calculateRobustnessMetrics(List<Solution> solutions) {
        if (solutions.size() < 2) {
            LoggingUtils.logWarning("Need at least 2 solutions for robustness analysis");
            return;
        }

        List<Double> objectives = solutions.stream()
                .map(Solution::getObjectiveValue)
                .filter(obj -> obj < Double.MAX_VALUE)
                .collect(Collectors.toList());

        if (objectives.size() < 2)
            return;

        double mean = objectives.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double stdDev = Math.sqrt(objectives.stream()
                .mapToDouble(obj -> Math.pow(obj - mean, 2))
                .average().orElse(0.0));

        // Robustness measures
        double robustnessIndex = mean > 0 ? stdDev / mean : 0.0; // Lower is more robust

        // Success rate (feasible solutions within 10% of best)
        double bestObjective = objectives.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double threshold = bestObjective * 1.1;

        long nearOptimalCount = solutions.stream()
                .filter(Solution::isFeasible)
                .mapToLong(s -> s.getObjectiveValue() <= threshold ? 1 : 0)
                .sum();

        double nearOptimalRate = (double) nearOptimalCount / solutions.size();

        metrics.put("robustness_index", robustnessIndex);
        metrics.put("near_optimal_rate", nearOptimalRate);
        metrics.put("solution_consistency", 1.0 - robustnessIndex); // Higher is more consistent

        LoggingUtils.logDebug("Robustness metrics calculated: index=" +
                String.format("%.3f", robustnessIndex));
    }

    private void calculateComparativeMetrics(List<Solution> solutions, ProblemInstance instance) {
        // Get correct MILP benchmark for this instance
        double milpBenchmark = getMilpBenchmark(instance);

        List<Double> feasibleObjectives = solutions.stream()
                .filter(Solution::isFeasible)
                .map(Solution::getObjectiveValue)
                .filter(obj -> obj < Double.MAX_VALUE)
                .collect(Collectors.toList());

        if (feasibleObjectives.isEmpty())
            return;

        double bestHeuristic = feasibleObjectives.stream()
                .mapToDouble(Double::doubleValue)
                .min().orElse(Double.MAX_VALUE);

        double avgHeuristic = feasibleObjectives.stream()
                .mapToDouble(Double::doubleValue)
                .average().orElse(Double.MAX_VALUE);

        // Gap calculations (percentage above MILP)
        double bestGap = ((bestHeuristic - milpBenchmark) / milpBenchmark) * 100;
        double avgGap = ((avgHeuristic - milpBenchmark) / milpBenchmark) * 100;

        // Performance rating based on corrected gaps
        String performanceRating;
        if (bestGap <= 20) // Within 20% of MILP
            performanceRating = "EXCELLENT";
        else if (bestGap <= 50) // Within 50% of MILP
            performanceRating = "GOOD";
        else if (bestGap <= 100) // Within 100% of MILP
            performanceRating = "ACCEPTABLE";
        else
            performanceRating = "NEEDS_IMPROVEMENT";

        metrics.put("milp_benchmark", milpBenchmark);
        metrics.put("best_heuristic_objective", bestHeuristic);
        metrics.put("avg_heuristic_objective", avgHeuristic);
        metrics.put("best_gap_percent", bestGap);
        metrics.put("avg_gap_percent", avgGap);

        LoggingUtils.logDebug("Comparative metrics calculated: MILP=" + milpBenchmark +
                ", best_gap=" + String.format("%.1f%%", bestGap) + " [" + performanceRating + "]");
    }

    /**
     * Print comprehensive metrics report
     */
    public void printMetrics() {
        if (metrics.isEmpty()) {
            LoggingUtils.logWarning("No metrics available to print. Call calculateMetrics() first.");
            return;
        }

        LoggingUtils.logSectionHeader("PERFORMANCE METRICS REPORT");

        // Objective Statistics
        printObjectiveMetrics();

        // Feasibility Analysis
        printFeasibilityMetrics();

        // Computation Performance
        printComputationMetrics();

        // Constraint Analysis
        printConstraintMetrics();

        // Quality Distribution
        printQualityMetrics();

        // Robustness Analysis
        printRobustnessMetrics();

        // Comparative Analysis (with corrected MILP benchmark)
        printComparativeMetrics();

        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("Metrics calculation time: " + calculationTime + "ms");
        LoggingUtils.logInfo("Total solutions analyzed: " + solutions.size());
    }

    private void printObjectiveMetrics() {
        LoggingUtils.logInfo("\nOBJECTIVE STATISTICS:");
        LoggingUtils.logInfo("  Mean Objective: " + formatMetric("objective_mean"));
        LoggingUtils.logInfo("  Best Objective: " + formatMetric("objective_min"));
        LoggingUtils.logInfo("  Worst Objective: " + formatMetric("objective_max"));
        LoggingUtils.logInfo("  Standard Deviation: " + formatMetric("objective_std_dev"));
        LoggingUtils.logInfo("  Coefficient of Variation: " + formatMetric("objective_cv", "%.3f"));
        LoggingUtils.logInfo("  Median: " + formatMetric("objective_median"));
        LoggingUtils.logInfo("  Q1: " + formatMetric("objective_q1"));
        LoggingUtils.logInfo("  Q3: " + formatMetric("objective_q3"));
    }

    private void printFeasibilityMetrics() {
        LoggingUtils.logInfo("\nFEASIBILITY ANALYSIS:");
        LoggingUtils.logInfo("  Feasibility Rate: " + formatMetric("feasibility_rate", "%.1f%%", 100.0));
        LoggingUtils.logInfo("  Feasible Solutions: " + formatMetric("feasible_solutions", "%.0f"));
        LoggingUtils.logInfo("  Infeasible Solutions: " + formatMetric("infeasible_solutions", "%.0f"));
        LoggingUtils.logInfo("  Avg Hard Violations: " + formatMetric("avg_hard_violations"));
        LoggingUtils.logInfo("  Max Hard Violations: " + formatMetric("max_hard_violations", "%.0f"));
        LoggingUtils.logInfo("  Avg Soft Violations: " + formatMetric("avg_soft_violations"));
    }

    private void printComputationMetrics() {
        LoggingUtils.logInfo("\nCOMPUTATION PERFORMANCE:");
        LoggingUtils.logInfo("  Average Time: " + formatMetric("avg_computation_time", "%.1f ms"));
        LoggingUtils.logInfo("  Fastest Time: " + formatMetric("min_computation_time", "%.0f ms"));
        LoggingUtils.logInfo("  Slowest Time: " + formatMetric("max_computation_time", "%.0f ms"));
        LoggingUtils.logInfo("  Total Time: " + formatMetric("total_computation_time", "%.0f ms"));
    }

    private void printConstraintMetrics() {
        LoggingUtils.logInfo("\nCONSTRAINT ANALYSIS:");

        // Print constraint-specific metrics
        Set<String> constraintTypes = metrics.keySet().stream()
                .filter(key -> key.startsWith("avg_"))
                .filter(key -> !key.equals("avg_hard_violations") && !key.equals("avg_soft_violations")
                        && !key.equals("avg_computation_time") && !key.equals("avg_heuristic_objective"))
                .collect(Collectors.toSet());

        for (String avgKey : constraintTypes) {
            String constraintType = avgKey.substring(4); // Remove "avg_"
            String satisfactionKey = "satisfaction_rate_" + constraintType;

            LoggingUtils.logInfo("  " + constraintType.toUpperCase() + ":");
            LoggingUtils.logInfo("    Avg Violations: " + formatMetric(avgKey));
            if (metrics.containsKey(satisfactionKey)) {
                LoggingUtils.logInfo("    Satisfaction Rate: " +
                        formatMetric(satisfactionKey, "%.1f%%", 100.0));
            }
        }
    }

    private void printQualityMetrics() {
        LoggingUtils.logInfo("\nQUALITY DISTRIBUTION:");
        LoggingUtils.logInfo("  Best Quarter Average: " + formatMetric("best_quarter_avg"));
        LoggingUtils.logInfo("  Worst Quarter Average: " + formatMetric("worst_quarter_avg"));
        LoggingUtils.logInfo("  Quality Spread: " + formatMetric("quality_spread"));
    }

    private void printRobustnessMetrics() {
        LoggingUtils.logInfo("\nROBUSTNESS ANALYSIS:");
        LoggingUtils.logInfo("  Robustness Index: " + formatMetric("robustness_index", "%.3f"));
        LoggingUtils.logInfo("  Solution Consistency: " + formatMetric("solution_consistency", "%.3f"));
        LoggingUtils.logInfo("  Near-Optimal Rate: " + formatMetric("near_optimal_rate", "%.1f%%", 100.0));
    }

    private void printComparativeMetrics() {
        LoggingUtils.logInfo("\nCOMPARATIVE ANALYSIS (CORRECTED MILP BENCHMARKS):");
        LoggingUtils.logInfo("  MILP Benchmark: " + formatMetric("milp_benchmark"));
        LoggingUtils.logInfo("  Best Heuristic: " + formatMetric("best_heuristic_objective"));
        LoggingUtils.logInfo("  Average Heuristic: " + formatMetric("avg_heuristic_objective"));
        LoggingUtils.logInfo("  Best Gap: " + formatMetric("best_gap_percent", "%+.1f%%"));
        LoggingUtils.logInfo("  Average Gap: " + formatMetric("avg_gap_percent", "%+.1f%%"));

        // Performance assessment with corrected ranges
        double bestGap = metrics.getOrDefault("best_gap_percent", Double.MAX_VALUE);
        String assessment;
        if (bestGap <= 20)
            assessment = "EXCELLENT - Within 20% of MILP";
        else if (bestGap <= 50)
            assessment = "GOOD - Within 50% of MILP";
        else if (bestGap <= 100)
            assessment = "ACCEPTABLE - Within 100% of MILP";
        else
            assessment = "NEEDS IMPROVEMENT - Gap > 100%";

        LoggingUtils.logInfo("  Performance Assessment: " + assessment);

        // Show which month's benchmark was used
        double milpBenchmark = metrics.getOrDefault("milp_benchmark", 0.0);
        String monthUsed = "Unknown";
        if (milpBenchmark == 201.0)
            monthUsed = "Month 1";
        else if (milpBenchmark == 997.0)
            monthUsed = "Month 2";
        else if (milpBenchmark == 1328.0)
            monthUsed = "Month 3";

        LoggingUtils.logInfo("  MILP Benchmark Used: " + monthUsed + " (" + milpBenchmark + ")");
    }

    /**
     * Export metrics to file
     */
    public void exportMetrics(String filePath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("PERFORMANCE METRICS REPORT (CORRECTED MILP BENCHMARKS)");
            writer.println("Generated: " + new Date());
            writer.println("Solutions Analyzed: " + solutions.size());
            writer.println("Calculation Time: " + calculationTime + "ms");
            writer.println();

            // Export all metrics
            writer.println("DETAILED METRICS:");
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                writer.printf("%-30s: %10.3f%n", entry.getKey(), entry.getValue());
            }

            writer.println();
            writer.println("MILP BENCHMARKS USED:");
            writer.println(
                    "Month 1: 201.0 (SC1:20 + SC2:10 + SC3:30 + SC4:16 + SC5:20 + SC6:90 + SC7:15 + SC8:0 + SC9:0 + SC10:0)");
            writer.println(
                    "Month 2: 997.0 (SC1:150 + SC2:10 + SC3:30 + SC4:24 + SC5:430 + SC6:220 + SC7:45 + SC8:32 + SC9:16 + SC10:40)");
            writer.println(
                    "Month 3: 1328.0 (SC1:150 + SC2:10 + SC3:30 + SC4:24 + SC5:730 + SC6:230 + SC7:66 + SC8:32 + SC9:16 + SC10:40)");

            writer.println();
            writer.println("DISTRIBUTION DATA:");
            for (Map.Entry<String, List<Double>> entry : distributionMetrics.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue().size() + " values");
                // Export first 10 values as sample
                List<Double> sample = entry.getValue().subList(0,
                        Math.min(10, entry.getValue().size()));
                writer.println("  Sample: " + sample);
            }

            LoggingUtils.logInfo("Performance metrics exported to: " + filePath);

        } catch (IOException e) {
            LoggingUtils.logError("Failed to export metrics: " + e.getMessage(), e);
        }
    }

    // Helper methods
    private String formatMetric(String key) {
        return formatMetric(key, "%.2f");
    }

    private String formatMetric(String key, String format) {
        Double value = metrics.get(key);
        return value != null ? String.format(format, value) : "N/A";
    }

    private String formatMetric(String key, String format, double multiplier) {
        Double value = metrics.get(key);
        return value != null ? String.format(format, value * multiplier) : "N/A";
    }

    private double getPercentile(List<Double> sortedList, int percentile) {
        if (sortedList.isEmpty())
            return 0.0;

        int index = (int) Math.ceil(percentile / 100.0 * sortedList.size()) - 1;
        index = Math.max(0, Math.min(index, sortedList.size() - 1));

        return sortedList.get(index);
    }

    // Getters for programmatic access
    public Map<String, Double> getMetrics() {
        return new HashMap<>(metrics);
    }

    public Map<String, List<Double>> getDistributionMetrics() {
        return new HashMap<>(distributionMetrics);
    }

    public double getMetric(String metricName) {
        return metrics.getOrDefault(metricName, 0.0);
    }

    public boolean hasMetric(String metricName) {
        return metrics.containsKey(metricName);
    }
}