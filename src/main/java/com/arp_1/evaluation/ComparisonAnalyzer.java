// File: src/main/java/com/arp_1/evaluation/ComparisonAnalyzer.java
package com.arp_1.evaluation;

import com.arp_1.main.MultipleRunsExperiment;
import java.util.*;

/**
 * Analyzes and compares different solution approaches with correct MILP
 * benchmarks
 */
public class ComparisonAnalyzer {
    // Updated MILP benchmarks from research paper MODEL column
    private static final Map<String, Double> MILP_BENCHMARKS = Map.of(
            "month1", 201.0, // SC1:20+SC2:10+SC3:30+SC4:16+SC5:20+SC6:90+SC7:15+SC8:0+SC9:0+SC10:0
            "month2", 271.0, // SC1:30+SC2:10+SC3:0+SC4:16+SC5:60+SC6:100+SC7:15+SC8:40+SC9:0+SC10:0
            "month3", 420.0 // SC1:50+SC2:20+SC3:30+SC4:16+SC5:50+SC6:190+SC7:24+SC8:8+SC9:24+SC10:8
    );

    private double currentBenchmark;
    private String currentDataset;
    private Map<String, Double> strategyBenchmarks;

    public ComparisonAnalyzer() {
        this.currentBenchmark = MILP_BENCHMARKS.get("month1"); // Default to month1
        this.currentDataset = "month1";
        this.strategyBenchmarks = new HashMap<>();
    }

    /**
     * Set benchmark for specific dataset/month
     */
    public void setBenchmark(String datasetName) {
        this.currentDataset = datasetName.toLowerCase();

        // Extract month identifier from dataset name
        String monthKey = extractMonthKey(datasetName);
        this.currentBenchmark = MILP_BENCHMARKS.getOrDefault(monthKey, MILP_BENCHMARKS.get("month1"));

        System.out.println("Set MILP benchmark for " + datasetName + " to " + currentBenchmark);
    }

    /**
     * Set the benchmark value manually (for backward compatibility)
     */
    public void setBenchmark(double benchmark) {
        this.currentBenchmark = benchmark;
    }

    /**
     * Get the current MILP benchmark value
     */
    public double getCurrentBenchmark() {
        return currentBenchmark;
    }

    /**
     * Get MILP benchmark for specific month
     */
    public double getMILPBenchmark(String monthIdentifier) {
        String monthKey = extractMonthKey(monthIdentifier);
        return MILP_BENCHMARKS.getOrDefault(monthKey, MILP_BENCHMARKS.get("month1"));
    }

    /**
     * Extract month key from dataset name (handles various naming conventions)
     */
    private String extractMonthKey(String datasetName) {
        String name = datasetName.toLowerCase();

        if (name.contains("month1") || name.contains("m1") || name.equals("1")) {
            return "month1";
        } else if (name.contains("month2") || name.contains("m2") || name.equals("2")) {
            return "month2";
        } else if (name.contains("month3") || name.contains("m3") || name.equals("3")) {
            return "month3";
        }

        // Default to month1 if cannot determine
        return "month1";
    }

    /**
     * Calculate gap percentage from current benchmark
     */
    public double calculateGap(double value) {
        return calculateGap(value, currentBenchmark);
    }

    /**
     * Calculate gap percentage from specific benchmark
     */
    public double calculateGap(double value, double benchmark) {
        if (benchmark == 0.0)
            return 0.0;
        return ((value - benchmark) / benchmark) * 100.0;
    }

    /**
     * Classify performance based on gap from MILP benchmark
     */
    public String classifyPerformance(double objectiveValue) {
        double gap = calculateGap(objectiveValue);
        double absGap = Math.abs(gap);

        if (absGap <= 5.0)
            return "EXCELLENT"; // Within 5% of MILP
        if (absGap <= 15.0)
            return "VERY_GOOD"; // Within 15% of MILP
        if (absGap <= 30.0)
            return "GOOD"; // Within 30% of MILP
        if (absGap <= 50.0)
            return "ACCEPTABLE"; // Within 50% of MILP
        if (absGap <= 100.0)
            return "POOR"; // Within 100% of MILP
        return "UNACCEPTABLE"; // More than 100% gap
    }

    /**
     * Compare multiple experiment results with correct MILP benchmarks
     */
    public ComparisonReport compareExperiments(List<MultipleRunsExperiment.ExperimentResult> results) {
        ComparisonReport report = new ComparisonReport();

        // Find best and worst performers
        MultipleRunsExperiment.ExperimentResult best = results.stream()
                .filter(r -> r.getFeasibilityRate() > 0.8) // At least 80% feasible
                .min(Comparator.comparingDouble(MultipleRunsExperiment.ExperimentResult::getMeanObjective))
                .orElse(results.stream()
                        .min(Comparator.comparingDouble(MultipleRunsExperiment.ExperimentResult::getMeanObjective))
                        .orElse(null));

        MultipleRunsExperiment.ExperimentResult worst = results.stream()
                .max(Comparator.comparingDouble(MultipleRunsExperiment.ExperimentResult::getMeanObjective))
                .orElse(null);

        report.bestStrategy = best;
        report.worstStrategy = worst;

        // Calculate performance rankings
        List<MultipleRunsExperiment.ExperimentResult> sortedResults = new ArrayList<>(results);
        sortedResults.sort(Comparator.comparingDouble(MultipleRunsExperiment.ExperimentResult::getMeanObjective));
        report.performanceRanking = sortedResults;

        // Calculate gaps from current benchmark
        report.benchmarkGaps = new HashMap<>();
        for (MultipleRunsExperiment.ExperimentResult result : results) {
            double gap = calculateGap(result.getMeanObjective());
            report.benchmarkGaps.put(result.getStrategyName(), gap);
        }

        // Statistical significance analysis
        report.statisticalSignificance = analyzeStatisticalSignificance(results);

        // Performance categories with updated thresholds
        report.performanceCategories = categorizePerformance(results);

        return report;
    }

    /**
     * Analyze statistical significance between strategies
     */
    private Map<String, String> analyzeStatisticalSignificance(List<MultipleRunsExperiment.ExperimentResult> results) {
        Map<String, String> significance = new HashMap<>();

        for (MultipleRunsExperiment.ExperimentResult result : results) {
            if (result.getNumRuns() > 1) {
                // Calculate confidence interval
                double confidenceInterval = 1.96 * result.getStdDevObjective() / Math.sqrt(result.getNumRuns());
                double lowerBound = result.getMeanObjective() - confidenceInterval;
                double upperBound = result.getMeanObjective() + confidenceInterval;

                // Check if significantly different from current benchmark
                if (upperBound < currentBenchmark) {
                    significance.put(result.getStrategyName(), "SIGNIFICANTLY_BETTER");
                } else if (lowerBound > currentBenchmark * 1.1) { // 10% worse threshold
                    significance.put(result.getStrategyName(), "SIGNIFICANTLY_WORSE");
                } else {
                    significance.put(result.getStrategyName(), "NO_SIGNIFICANT_DIFFERENCE");
                }
            } else {
                significance.put(result.getStrategyName(), "INSUFFICIENT_DATA");
            }
        }

        return significance;
    }

    /**
     * Categorize strategies by performance level with updated thresholds
     */
    private Map<String, List<String>> categorizePerformance(List<MultipleRunsExperiment.ExperimentResult> results) {
        Map<String, List<String>> categories = new HashMap<>();
        categories.put("EXCELLENT", new ArrayList<>()); // Within 5% of MILP
        categories.put("VERY_GOOD", new ArrayList<>()); // Within 15% of MILP
        categories.put("GOOD", new ArrayList<>()); // Within 30% of MILP
        categories.put("ACCEPTABLE", new ArrayList<>()); // Within 50% of MILP
        categories.put("POOR", new ArrayList<>()); // Within 100% of MILP
        categories.put("UNACCEPTABLE", new ArrayList<>()); // More than 100% gap

        for (MultipleRunsExperiment.ExperimentResult result : results) {
            double gap = Math.abs(calculateGap(result.getMeanObjective()));
            String category;

            if (gap <= 5.0) {
                category = "EXCELLENT";
            } else if (gap <= 15.0) {
                category = "VERY_GOOD";
            } else if (gap <= 30.0) {
                category = "GOOD";
            } else if (gap <= 50.0) {
                category = "ACCEPTABLE";
            } else if (gap <= 100.0) {
                category = "POOR";
            } else {
                category = "UNACCEPTABLE";
            }

            categories.get(category).add(result.getStrategyName());
        }

        return categories;
    }

    /**
     * Generate performance summary for current dataset
     */
    public String generatePerformanceSummary(List<MultipleRunsExperiment.ExperimentResult> results) {
        StringBuilder summary = new StringBuilder();
        summary.append("=== PERFORMANCE ANALYSIS SUMMARY ===\n");
        summary.append("Dataset: ").append(currentDataset).append("\n");
        summary.append("MILP Benchmark: ").append(currentBenchmark).append("\n\n");

        // Sort by performance
        List<MultipleRunsExperiment.ExperimentResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingDouble(MultipleRunsExperiment.ExperimentResult::getMeanObjective));

        summary.append("Strategy Performance Ranking:\n");
        for (int i = 0; i < sorted.size(); i++) {
            MultipleRunsExperiment.ExperimentResult result = sorted.get(i);
            double gap = calculateGap(result.getMeanObjective());
            String performance = classifyPerformance(result.getMeanObjective());

            summary.append(String.format("%2d. %-30s: %6.1f (%+5.1f%%) [%s]\n",
                    i + 1, result.getStrategyName(), result.getMeanObjective(), gap, performance));
        }

        // Category summary
        Map<String, List<String>> categories = categorizePerformance(results);
        summary.append("\nPerformance Categories:\n");
        for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                summary.append(String.format("%-15s: %d strategies\n",
                        entry.getKey(), entry.getValue().size()));
            }
        }

        return summary.toString();
    }

    /**
     * Print detailed comparison against all month benchmarks
     */
    public void printMultiMonthComparison(MultipleRunsExperiment.ExperimentResult result) {
        System.out.println("\n=== MULTI-MONTH MILP COMPARISON ===");
        System.out.println("Strategy: " + result.getStrategyName());
        System.out.println("Mean Objective: " + String.format("%.1f", result.getMeanObjective()));
        System.out.println();

        for (Map.Entry<String, Double> entry : MILP_BENCHMARKS.entrySet()) {
            String month = entry.getKey();
            double benchmark = entry.getValue();
            double gap = calculateGap(result.getMeanObjective(), benchmark);
            String performance = classifyPerformanceForBenchmark(result.getMeanObjective(), benchmark);

            System.out.printf("%-8s: MILP=%6.0f, Gap=%+5.1f%% [%s]\n",
                    month.toUpperCase(), benchmark, gap, performance);
        }
    }

    private String classifyPerformanceForBenchmark(double value, double benchmark) {
        double gap = Math.abs(calculateGap(value, benchmark));
        if (gap <= 5.0)
            return "EXCELLENT";
        if (gap <= 15.0)
            return "VERY_GOOD";
        if (gap <= 30.0)
            return "GOOD";
        if (gap <= 50.0)
            return "ACCEPTABLE";
        if (gap <= 100.0)
            return "POOR";
        return "UNACCEPTABLE";
    }

    // Rest of the existing methods remain the same...
    public Map<String, Map<String, Double>> generatePairwiseComparisons(
            List<MultipleRunsExperiment.ExperimentResult> results) {
        Map<String, Map<String, Double>> comparisons = new HashMap<>();

        for (MultipleRunsExperiment.ExperimentResult result1 : results) {
            Map<String, Double> pairwiseGaps = new HashMap<>();

            for (MultipleRunsExperiment.ExperimentResult result2 : results) {
                if (!result1.getStrategyName().equals(result2.getStrategyName())) {
                    double gap = calculateGap(result1.getMeanObjective(), result2.getMeanObjective());
                    pairwiseGaps.put(result2.getStrategyName(), gap);
                }
            }

            comparisons.put(result1.getStrategyName(), pairwiseGaps);
        }

        return comparisons;
    }

    public void exportComparison(List<MultipleRunsExperiment.ExperimentResult> results, String filename) {
        try {
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename));

            // Write header with current benchmark info
            writer.println("Strategy,Runs,Mean_Objective,Std_Dev,Best,Worst,Median,Feasibility_Rate," +
                    "Mean_Time_ms,Gap_from_MILP_%,MILP_Benchmark,Performance_Category,Statistical_Significance");

            // Generate comparison report
            ComparisonReport report = compareExperiments(results);

            // Write data for each strategy
            for (MultipleRunsExperiment.ExperimentResult result : results) {
                double gap = calculateGap(result.getMeanObjective());
                String category = classifyPerformance(result.getMeanObjective());
                String significance = report.statisticalSignificance.getOrDefault(
                        result.getStrategyName(), "UNKNOWN");

                writer.printf("%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%.2f,%.0f,%s,%s\n",
                        result.getStrategyName(),
                        result.getNumRuns(),
                        result.getMeanObjective(),
                        result.getStdDevObjective(),
                        result.getBestObjective(),
                        result.getWorstObjective(),
                        result.getMedianObjective(),
                        result.getFeasibilityRate(),
                        result.getMeanComputationTime(),
                        gap,
                        currentBenchmark,
                        category,
                        significance);
            }

            writer.close();

            // Also export detailed comparison matrix
            exportComparisonMatrix(results, filename.replace(".csv", "_matrix.csv"));

        } catch (Exception e) {
            throw new RuntimeException("Failed to export comparison results: " + e.getMessage(), e);
        }
    }

    private void exportComparisonMatrix(List<MultipleRunsExperiment.ExperimentResult> results, String filename) {
        try {
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename));

            // Generate pairwise comparisons
            Map<String, Map<String, Double>> pairwiseComparisons = generatePairwiseComparisons(results);

            // Write header
            writer.print("Strategy");
            for (MultipleRunsExperiment.ExperimentResult result : results) {
                writer.print("," + result.getStrategyName());
            }
            writer.println();

            // Write comparison matrix
            for (MultipleRunsExperiment.ExperimentResult result1 : results) {
                writer.print(result1.getStrategyName());

                for (MultipleRunsExperiment.ExperimentResult result2 : results) {
                    if (result1.getStrategyName().equals(result2.getStrategyName())) {
                        writer.print(",0.0"); // Same strategy
                    } else {
                        Double gap = pairwiseComparisons
                                .getOrDefault(result1.getStrategyName(), new HashMap<>())
                                .getOrDefault(result2.getStrategyName(), 0.0);
                        writer.printf(",%.2f", gap);
                    }
                }
                writer.println();
            }

            writer.close();

        } catch (Exception e) {
            throw new RuntimeException("Failed to export comparison matrix: " + e.getMessage(), e);
        }
    }

    public void exportPerformanceAnalysis(List<MultipleRunsExperiment.ExperimentResult> results,
            String filename) {
        try {
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename));

            ComparisonReport report = compareExperiments(results);

            // Write analysis header with correct benchmark
            writer.println("# Performance Analysis Report");
            writer.println("# Generated: " + java.time.LocalDateTime.now());
            writer.println("# Dataset: " + currentDataset);
            writer.println("# MILP Benchmark: " + currentBenchmark);
            writer.println();

            // Best and worst performers
            if (report.bestStrategy != null) {
                writer.println("Best Strategy: " + report.bestStrategy.getStrategyName());
                writer.printf("Best Objective: %.3f\n", report.bestStrategy.getMeanObjective());
                writer.printf("Best Gap: %.2f%%\n", calculateGap(report.bestStrategy.getMeanObjective()));
            }

            if (report.worstStrategy != null) {
                writer.println("Worst Strategy: " + report.worstStrategy.getStrategyName());
                writer.printf("Worst Objective: %.3f\n", report.worstStrategy.getMeanObjective());
                writer.printf("Worst Gap: %.2f%%\n", calculateGap(report.worstStrategy.getMeanObjective()));
            }

            writer.println();

            // Performance categories
            writer.println("# Performance Categories");
            for (Map.Entry<String, List<String>> entry : report.performanceCategories.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue());
            }

            writer.println();

            // Statistical significance
            writer.println("# Statistical Significance");
            for (Map.Entry<String, String> entry : report.statisticalSignificance.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue());
            }

            writer.close();

        } catch (Exception e) {
            throw new RuntimeException("Failed to export performance analysis: " + e.getMessage(), e);
        }
    }

    /**
     * Comprehensive comparison report with updated benchmarks
     */
    public static class ComparisonReport {
        public MultipleRunsExperiment.ExperimentResult bestStrategy;
        public MultipleRunsExperiment.ExperimentResult worstStrategy;
        public List<MultipleRunsExperiment.ExperimentResult> performanceRanking;
        public Map<String, Double> benchmarkGaps;
        public Map<String, String> statisticalSignificance;
        public Map<String, List<String>> performanceCategories;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ComparisonReport{\n");

            if (bestStrategy != null) {
                sb.append("  Best Strategy: ").append(bestStrategy.getStrategyName())
                        .append(" (").append(String.format("%.2f", bestStrategy.getMeanObjective())).append(")\n");
            }

            if (worstStrategy != null) {
                sb.append("  Worst Strategy: ").append(worstStrategy.getStrategyName())
                        .append(" (").append(String.format("%.2f", worstStrategy.getMeanObjective())).append(")\n");
            }

            sb.append("  Performance Categories:\n");
            for (Map.Entry<String, List<String>> entry : performanceCategories.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    sb.append("    ").append(entry.getKey()).append(": ")
                            .append(entry.getValue()).append("\n");
                }
            }

            sb.append("}");
            return sb.toString();
        }
    }
}