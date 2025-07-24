// File: src/main/java/com/arp/evaluation/ComparisonAnalyzer.java
package com.arp_1.evaluation;

import com.arp_1.main.MultipleRunsExperiment;
import java.util.*;

/**
 * Analyzes and compares different solution approaches
 */
public class ComparisonAnalyzer {
    private double benchmarkValue;
    private Map<String, Double> strategyBenchmarks;

    public ComparisonAnalyzer() {
        this.benchmarkValue = 70.0; // Default MILP benchmark
        this.strategyBenchmarks = new HashMap<>();
    }

    /**
     * Set the benchmark value for comparison
     */
    public void setBenchmark(double benchmark) {
        this.benchmarkValue = benchmark;
    }

    /**
     * Calculate gap percentage from benchmark
     */
    public double calculateGap(double value, double benchmark) {
        if (benchmark == 0.0)
            return 0.0;
        return ((value - benchmark) / benchmark) * 100.0;
    }

    /**
     * Calculate gap from the set benchmark
     */
    public double calculateGap(double value) {
        return calculateGap(value, benchmarkValue);
    }

    /**
     * Compare multiple experiment results
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

        // Calculate gaps from benchmark
        report.benchmarkGaps = new HashMap<>();
        for (MultipleRunsExperiment.ExperimentResult result : results) {
            double gap = calculateGap(result.getMeanObjective());
            report.benchmarkGaps.put(result.getStrategyName(), gap);
        }

        // Statistical significance analysis
        report.statisticalSignificance = analyzeStatisticalSignificance(results);

        // Performance categories
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

                // Check if significantly different from benchmark
                if (upperBound < benchmarkValue) {
                    significance.put(result.getStrategyName(), "SIGNIFICANTLY_BETTER");
                } else if (lowerBound > benchmarkValue) {
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
     * Categorize strategies by performance level
     */
    private Map<String, List<String>> categorizePerformance(List<MultipleRunsExperiment.ExperimentResult> results) {
        Map<String, List<String>> categories = new HashMap<>();
        categories.put("EXCELLENT", new ArrayList<>()); // Within 20% of benchmark
        categories.put("GOOD", new ArrayList<>()); // Within 50% of benchmark
        categories.put("ACCEPTABLE", new ArrayList<>()); // Within 100% of benchmark
        categories.put("POOR", new ArrayList<>()); // More than 100% from benchmark

        for (MultipleRunsExperiment.ExperimentResult result : results) {
            double gap = Math.abs(calculateGap(result.getMeanObjective()));
            String category;

            if (gap <= 20.0) {
                category = "EXCELLENT";
            } else if (gap <= 50.0) {
                category = "GOOD";
            } else if (gap <= 100.0) {
                category = "ACCEPTABLE";
            } else {
                category = "POOR";
            }

            categories.get(category).add(result.getStrategyName());
        }

        return categories;
    }

    /**
     * Generate pairwise comparisons
     */
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

    /**
     * Export comparison results to CSV file
     */
    public void exportComparison(List<MultipleRunsExperiment.ExperimentResult> results, String filename) {
        try {
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename));

            // Write header
            writer.println("Strategy,Runs,Mean_Objective,Std_Dev,Best,Worst,Median,Feasibility_Rate," +
                    "Mean_Time_ms,Gap_from_Benchmark_%,Performance_Category,Statistical_Significance");

            // Generate comparison report
            ComparisonReport report = compareExperiments(results);

            // Write data for each strategy
            for (MultipleRunsExperiment.ExperimentResult result : results) {
                double gap = calculateGap(result.getMeanObjective());
                String category = categorizePerformance(gap);
                String significance = report.statisticalSignificance.getOrDefault(
                        result.getStrategyName(), "UNKNOWN");

                writer.printf("%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%.2f,%s,%s%n",
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

    /**
     * Export pairwise comparison matrix
     */
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

    /**
     * Categorize performance based on gap from benchmark
     */
    private String categorizePerformance(double gap) {
        double absGap = Math.abs(gap);
        if (absGap <= 10.0)
            return "EXCELLENT";
        if (absGap <= 20.0)
            return "GOOD";
        if (absGap <= 30.0)
            return "ACCEPTABLE";
        if (absGap <= 50.0)
            return "POOR";
        return "UNACCEPTABLE";
    }

    /**
     * Export detailed performance analysis
     */
    public void exportPerformanceAnalysis(List<MultipleRunsExperiment.ExperimentResult> results,
            String filename) {
        try {
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename));

            ComparisonReport report = compareExperiments(results);

            // Write analysis header
            writer.println("# Performance Analysis Report");
            writer.println("# Generated: " + java.time.LocalDateTime.now());
            writer.println("# Benchmark: " + benchmarkValue);
            writer.println();

            // Best and worst performers
            if (report.bestStrategy != null) {
                writer.println("Best Strategy: " + report.bestStrategy.getStrategyName());
                writer.printf("Best Objective: %.3f%n", report.bestStrategy.getMeanObjective());
                writer.printf("Best Gap: %.2f%%%n", calculateGap(report.bestStrategy.getMeanObjective()));
            }

            if (report.worstStrategy != null) {
                writer.println("Worst Strategy: " + report.worstStrategy.getStrategyName());
                writer.printf("Worst Objective: %.3f%n", report.worstStrategy.getMeanObjective());
                writer.printf("Worst Gap: %.2f%%%n", calculateGap(report.worstStrategy.getMeanObjective()));
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
     * Comprehensive comparison report
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
