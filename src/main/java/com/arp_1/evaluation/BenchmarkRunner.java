// File: src/main/java/com/arp_1/evaluation/BenchmarkRunner.java
package com.arp_1.evaluation;

import com.arp_1.core.data.DataLoader;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.core.data.ValidationUtils;
import com.arp_1.core.models.Solution;
import com.arp_1.heuristics.base.*;
import com.arp_1.heuristics.strategies.*;
import com.arp_1.utils.LoggingUtils;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Comprehensive benchmark runner for comparing heuristic strategies against
 * MILP benchmarks with correct penalty values for months 1, 2, and 3
 */
public class BenchmarkRunner {
    // CORRECTED MILP benchmark objectives based on MODEL column from research paper
    private static final Map<String, Double> MILP_BENCHMARK_OBJECTIVES = Map.of(
            "month1", 201.0, // 20+10+30+16+20+90+15+0+0+0 = 201
            "month2", 271.0, // 30+10+0+16+60+100+15+40+0+0 = 271 (partial sum visible), using calculated
                             // total
            "month3", 420.0 // 50+20+30+16+50+190+24+8+24+8 = 420 (partial sum visible), using calculated
                            // total
    );

    private static final double EXCELLENT_GAP_THRESHOLD = 20.0; // 20% above MILP
    private static final double GOOD_GAP_THRESHOLD = 50.0; // 50% above MILP

    private ProblemInstance instance;
    private List<BenchmarkResult> results;
    private String outputDirectory;
    private ExecutorService executorService;
    private String datasetName; // To determine which MILP benchmark to use

    public BenchmarkRunner(ProblemInstance instance) {
        this.instance = instance;
        this.results = new ArrayList<>();
        this.outputDirectory = "results/benchmark_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.datasetName = detectDatasetName(instance);

        // Create output directory
        new File(outputDirectory).mkdirs();
    }

    /**
     * Detect dataset name from problem instance to determine correct MILP benchmark
     */
    private String detectDatasetName(ProblemInstance instance) {
        // Try to detect from instance metadata or default to month1
        String instanceString = instance.toString().toLowerCase();
        if (instanceString.contains("month1") || instanceString.contains("1")) {
            return "month1";
        } else if (instanceString.contains("month2") || instanceString.contains("2")) {
            return "month2";
        } else if (instanceString.contains("month3") || instanceString.contains("3")) {
            return "month3";
        }

        // Default to month1 if cannot detect
        LoggingUtils.logWarning("Cannot detect dataset month, defaulting to month1 MILP benchmark");
        return "month1";
    }

    /**
     * Get the correct MILP benchmark for current dataset
     */
    private double getMILPBenchmark() {
        return MILP_BENCHMARK_OBJECTIVES.getOrDefault(datasetName, MILP_BENCHMARK_OBJECTIVES.get("month1"));
    }

    /**
     * Run comprehensive benchmark with all strategies
     */
    public List<BenchmarkResult> runComprehensiveBenchmark(int runsPerStrategy) {
        double milpBenchmark = getMILPBenchmark();

        LoggingUtils.logSectionHeader("COMPREHENSIVE HEURISTIC BENCHMARK");
        LoggingUtils.logInfo("Dataset: " + datasetName);
        LoggingUtils.logInfo("MILP Benchmark Target: " + milpBenchmark);
        LoggingUtils.logInfo("Runs per strategy: " + runsPerStrategy);
        LoggingUtils.logInfo("Output directory: " + outputDirectory);

        List<BenchmarkConfiguration> configurations = createBenchmarkConfigurations();

        LoggingUtils.logInfo("Total configurations to test: " + configurations.size());
        LoggingUtils.logSeparator();

        // Run benchmarks for each configuration
        for (BenchmarkConfiguration config : configurations) {
            LoggingUtils.logInfo("Running benchmark: " + config.getName());
            BenchmarkResult result = runStrategyBenchmark(config, runsPerStrategy);
            results.add(result);

            // Log immediate results with correct MILP comparison
            double gap = ((result.getMeanObjective() - milpBenchmark) / milpBenchmark) * 100;
            LoggingUtils.logInfo("Completed " + config.getName() +
                    " - Mean Objective: " + String.format("%.2f", result.getMeanObjective()) +
                    " - Gap: " + String.format("%.1f%%", gap));
        }

        // Analyze and export results
        analyzeResults();
        exportResults();

        return new ArrayList<>(results);
    }

    /**
     * Create all benchmark configurations to test
     */
    private List<BenchmarkConfiguration> createBenchmarkConfigurations() {
        List<BenchmarkConfiguration> configurations = new ArrayList<>();

        // LocationAware Strategy - Deterministic
        configurations.add(new BenchmarkConfiguration(
                "LocationAware_Deterministic",
                "Deterministic location-aware sequential construction",
                () -> LocationAwareSequentialConstruction.createDeterministic()));

        // LocationAware Strategy - Randomized with different bias levels
        double[] biasLevels = { 0.0, 0.2, 0.4, 0.6, 0.8, 1.0 };
        for (double bias : biasLevels) {
            configurations.add(new BenchmarkConfiguration(
                    "LocationAware_Random_" + String.format("%.1f", bias),
                    "Randomized location-aware with bias " + bias,
                    () -> LocationAwareSequentialConstruction.createRandomized(
                            System.currentTimeMillis() + (long) (bias * 1000), bias)));
        }

        // Hierarchical Strategy
        configurations.add(new BenchmarkConfiguration(
                "Hierarchical_Deterministic",
                "Hierarchical constraint construction - deterministic",
                () -> HierarchicalConstraintConstruction.createDeterministic()));

        configurations.add(new BenchmarkConfiguration(
                "Hierarchical_Random_0.5",
                "Hierarchical constraint construction - randomized",
                () -> HierarchicalConstraintConstruction.createRandomized(
                        System.currentTimeMillis(), 0.5)));

        // Adaptive Strategy
        configurations.add(new BenchmarkConfiguration(
                "Adaptive_Random_0.3",
                "Adaptive priority construction - low randomness",
                () -> AdaptivePriorityConstruction.createRandomized(
                        System.currentTimeMillis(), 0.3)));

        configurations.add(new BenchmarkConfiguration(
                "Adaptive_Random_0.7",
                "Adaptive priority construction - high randomness",
                () -> AdaptivePriorityConstruction.createRandomized(
                        System.currentTimeMillis(), 0.7)));

        // TimeDecomposed Strategy
        configurations.add(new BenchmarkConfiguration(
                "TimeDecomposed_Deterministic",
                "Time decomposed construction - deterministic",
                () -> TimeDecomposedConstruction.createDeterministic()));

        configurations.add(new BenchmarkConfiguration(
                "TimeDecomposed_Random_0.4",
                "Time decomposed construction - randomized",
                () -> TimeDecomposedConstruction.createRandomized(
                        System.currentTimeMillis(), 0.4)));

        return configurations;
    }

    /**
     * Run benchmark for a specific strategy configuration
     */
    private BenchmarkResult runStrategyBenchmark(BenchmarkConfiguration config, int numRuns) {
        List<CompletableFuture<BenchmarkRun>> futures = new ArrayList<>();

        // Create parallel benchmark runs
        for (int run = 0; run < numRuns; run++) {
            final int runId = run;
            CompletableFuture<BenchmarkRun> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return executeingleBenchmarkRun(config, runId);
                } catch (Exception e) {
                    LoggingUtils.logError("Benchmark run " + runId + " failed for " + config.getName(), e);
                    return new BenchmarkRun(runId, Double.MAX_VALUE, 0L, false, e.getMessage());
                }
            }, executorService);

            futures.add(future);
        }

        // Collect results
        List<BenchmarkRun> runs = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        return new BenchmarkResult(config, runs, getMILPBenchmark());
    }

    /**
     * Execute a single benchmark run
     */
    private BenchmarkRun executeingleBenchmarkRun(BenchmarkConfiguration config, int runId) {
        long startTime = System.currentTimeMillis();

        try {
            // Create fresh heuristic instance
            ConstructiveHeuristic heuristic = config.getHeuristicFactory().create();

            // Configure logging for this run
            LoggingUtils.setLogLevel(LoggingUtils.LogLevel.WARNING); // Reduce logging noise

            // Execute construction
            Solution solution = heuristic.constructSolution(instance);

            long endTime = System.currentTimeMillis();
            long computationTime = endTime - startTime;

            // Validate solution
            boolean isFeasible = solution.isFeasible();
            double objectiveValue = solution.getObjectiveValue();

            // Log run completion
            if ((runId + 1) % 10 == 0) {
                LoggingUtils.logProgress(config.getName(), runId + 1,
                        config.getName().contains("Deterministic") ? 1 : 30);
            }

            return new BenchmarkRun(runId, objectiveValue, computationTime, isFeasible, null);

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            return new BenchmarkRun(runId, Double.MAX_VALUE, endTime - startTime, false, e.getMessage());
        } finally {
            LoggingUtils.setLogLevel(LoggingUtils.LogLevel.INFO); // Restore logging
        }
    }

    /**
     * Analyze benchmark results and identify best strategies
     */
    private void analyzeResults() {
        double milpBenchmark = getMILPBenchmark();

        LoggingUtils.logSectionHeader("BENCHMARK ANALYSIS RESULTS");
        LoggingUtils.logInfo("Using MILP benchmark: " + milpBenchmark + " for dataset: " + datasetName);

        // Sort results by mean objective (best first)
        List<BenchmarkResult> sortedResults = results.stream()
                .filter(r -> r.getFeasibilityRate() > 0.5) // At least 50% feasible
                .sorted(Comparator.comparingDouble(BenchmarkResult::getMeanObjective))
                .collect(Collectors.toList());

        if (sortedResults.isEmpty()) {
            LoggingUtils.logError("No feasible strategies found!");
            return;
        }

        // Identify best strategies
        BenchmarkResult bestOverall = sortedResults.get(0);
        BenchmarkResult bestFeasible = results.stream()
                .filter(r -> r.getFeasibilityRate() >= 0.95) // At least 95% feasible
                .min(Comparator.comparingDouble(BenchmarkResult::getMeanObjective))
                .orElse(bestOverall);

        // Print summary with correct MILP gaps
        LoggingUtils.logInfo("BEST OVERALL STRATEGY:");
        LoggingUtils.logInfo("  " + bestOverall.getConfiguration().getName());
        LoggingUtils.logInfo("  Mean Objective: " + String.format("%.2f", bestOverall.getMeanObjective()));
        LoggingUtils.logInfo("  MILP Gap: " + String.format("%.1f%%", bestOverall.getMilpGapPercentage()));
        LoggingUtils.logInfo("  Feasibility: " + String.format("%.1f%%", bestOverall.getFeasibilityRate() * 100));

        LoggingUtils.logInfo("\nBEST RELIABLE STRATEGY (95%+ feasible):");
        LoggingUtils.logInfo("  " + bestFeasible.getConfiguration().getName());
        LoggingUtils.logInfo("  Mean Objective: " + String.format("%.2f", bestFeasible.getMeanObjective()));
        LoggingUtils.logInfo("  MILP Gap: " + String.format("%.1f%%", bestFeasible.getMilpGapPercentage()));
        LoggingUtils.logInfo("  Feasibility: " + String.format("%.1f%%", bestFeasible.getFeasibilityRate() * 100));

        // Strategy performance categories
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("STRATEGY PERFORMANCE CATEGORIES:");
        LoggingUtils.logSeparator();

        for (BenchmarkResult result : results) {
            String category = categorizePerformance(result);
            LoggingUtils.logInfo(String.format("%-30s: %s (%.1f%% gap, %.1f%% feasible)",
                    result.getConfiguration().getName(),
                    category,
                    result.getMilpGapPercentage(),
                    result.getFeasibilityRate() * 100));
        }

        // Statistical significance analysis
        performStatisticalAnalysis(sortedResults);
    }

    /**
     * Categorize strategy performance based on corrected MILP gaps
     */
    private String categorizePerformance(BenchmarkResult result) {
        if (result.getFeasibilityRate() < 0.8) {
            return "UNRELIABLE";
        } else if (result.getMilpGapPercentage() <= EXCELLENT_GAP_THRESHOLD) {
            return "EXCELLENT";
        } else if (result.getMilpGapPercentage() <= GOOD_GAP_THRESHOLD) {
            return "GOOD";
        } else {
            return "NEEDS_IMPROVEMENT";
        }
    }

    /**
     * Perform statistical significance analysis
     */
    private void performStatisticalAnalysis(List<BenchmarkResult> sortedResults) {
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("STATISTICAL SIGNIFICANCE ANALYSIS:");
        LoggingUtils.logSeparator();

        if (sortedResults.size() < 2) {
            LoggingUtils.logInfo("Insufficient strategies for statistical comparison");
            return;
        }

        BenchmarkResult best = sortedResults.get(0);

        for (int i = 1; i < Math.min(5, sortedResults.size()); i++) {
            BenchmarkResult other = sortedResults.get(i);

            // Perform t-test comparison
            double tStatistic = calculateTStatistic(best, other);
            boolean significant = Math.abs(tStatistic) > 1.96; // 95% confidence

            LoggingUtils.logInfo(String.format("%-25s vs %-25s: t=%.3f (%s)",
                    best.getConfiguration().getName(),
                    other.getConfiguration().getName(),
                    tStatistic,
                    significant ? "SIGNIFICANT" : "not significant"));
        }
    }

    /**
     * Calculate t-statistic for comparing two benchmark results
     */
    private double calculateTStatistic(BenchmarkResult result1, BenchmarkResult result2) {
        double mean1 = result1.getMeanObjective();
        double mean2 = result2.getMeanObjective();
        double std1 = result1.getStdDevObjective();
        double std2 = result2.getStdDevObjective();
        int n1 = result1.getValidRunCount();
        int n2 = result2.getValidRunCount();

        if (n1 <= 1 || n2 <= 1 || std1 == 0 || std2 == 0) {
            return 0.0; // Cannot compute t-statistic
        }

        double pooledStd = Math.sqrt(((n1 - 1) * std1 * std1 + (n2 - 1) * std2 * std2) / (n1 + n2 - 2));
        double standardError = pooledStd * Math.sqrt(1.0 / n1 + 1.0 / n2);

        return (mean1 - mean2) / standardError;
    }

    /**
     * Export benchmark results to files
     */
    private void exportResults() {
        LoggingUtils.logInfo("Exporting benchmark results to: " + outputDirectory);

        try {
            // Export summary CSV
            exportSummaryCSV();

            // Export detailed results
            exportDetailedResults();

            // Export analysis report
            exportAnalysisReport();

            LoggingUtils.logInfo("Benchmark results exported successfully");

        } catch (IOException e) {
            LoggingUtils.logError("Failed to export benchmark results", e);
        }
    }

    /**
     * Export summary CSV file
     */
    private void exportSummaryCSV() throws IOException {
        File csvFile = new File(outputDirectory, "benchmark_summary.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            // Header
            writer.println("Strategy,Description,Runs,MeanObjective,StdDev,BestObjective,WorstObjective," +
                    "MeanTime(ms),FeasibilityRate,MILPGap%,Category,Dataset,MILPBenchmark");

            // Data rows
            double milpBenchmark = getMILPBenchmark();
            for (BenchmarkResult result : results) {
                BenchmarkConfiguration config = result.getConfiguration();
                writer.printf("%s,%s,%d,%.3f,%.3f,%.3f,%.3f,%.1f,%.3f,%.2f,%s,%s,%.1f%n",
                        config.getName(),
                        config.getDescription().replace(",", ";"),
                        result.getTotalRuns(),
                        result.getMeanObjective(),
                        result.getStdDevObjective(),
                        result.getBestObjective(),
                        result.getWorstObjective(),
                        result.getMeanComputationTime(),
                        result.getFeasibilityRate(),
                        result.getMilpGapPercentage(),
                        categorizePerformance(result),
                        datasetName,
                        milpBenchmark);
            }
        }

        LoggingUtils.logInfo("Summary CSV exported: " + csvFile.getAbsolutePath());
    }

    /**
     * Export detailed results for each run
     */
    private void exportDetailedResults() throws IOException {
        File detailFile = new File(outputDirectory, "benchmark_detailed.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(detailFile))) {
            // Header
            writer.println("Strategy,RunID,Objective,ComputationTime(ms),Feasible,ErrorMessage,Dataset,MILPBenchmark");

            // Data rows
            double milpBenchmark = getMILPBenchmark();
            for (BenchmarkResult result : results) {
                String strategyName = result.getConfiguration().getName();

                for (BenchmarkRun run : result.getRuns()) {
                    writer.printf("%s,%d,%.3f,%d,%s,%s,%s,%.1f%n",
                            strategyName,
                            run.getRunId(),
                            run.getObjectiveValue(),
                            run.getComputationTime(),
                            run.isFeasible(),
                            run.getErrorMessage() != null ? run.getErrorMessage().replace(",", ";") : "",
                            datasetName,
                            milpBenchmark);
                }
            }
        }

        LoggingUtils.logInfo("Detailed results exported: " + detailFile.getAbsolutePath());
    }

    /**
     * Export analysis report
     */
    private void exportAnalysisReport() throws IOException {
        File reportFile = new File(outputDirectory, "benchmark_analysis_report.txt");
        double milpBenchmark = getMILPBenchmark();

        try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile))) {
            writer.println("ANAESTHETIST ROSTERING PROBLEM - HEURISTIC BENCHMARK REPORT");
            writer.println("=" + "=".repeat(70));
            writer.println(
                    "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println("Dataset: " + datasetName);
            writer.println("MILP Benchmark Target: " + milpBenchmark);
            writer.println("Problem Instance: " + instance.toString());
            writer.println();

            // Best strategies
            BenchmarkResult best = results.stream()
                    .filter(r -> r.getFeasibilityRate() > 0.5)
                    .min(Comparator.comparingDouble(BenchmarkResult::getMeanObjective))
                    .orElse(null);

            if (best != null) {
                writer.println("BEST PERFORMING STRATEGY:");
                writer.println("  Name: " + best.getConfiguration().getName());
                writer.println("  Description: " + best.getConfiguration().getDescription());
                writer.println("  Mean Objective: " + String.format("%.3f", best.getMeanObjective()));
                writer.println("  MILP Gap: " + String.format("%.2f%%", best.getMilpGapPercentage()));
                writer.println("  Feasibility Rate: " + String.format("%.1f%%", best.getFeasibilityRate() * 100));
                writer.println("  Standard Deviation: " + String.format("%.3f", best.getStdDevObjective()));
                writer.println();
            }

            // Strategy comparison table
            writer.println("COMPLETE STRATEGY COMPARISON:");
            writer.println("-" + "-".repeat(70));
            writer.printf("%-30s %12s %10s %12s %10s%n", "Strategy", "Mean Obj", "MILP Gap%", "Feasible%", "Category");
            writer.println("-" + "-".repeat(70));

            results.stream()
                    .sorted(Comparator.comparingDouble(BenchmarkResult::getMeanObjective))
                    .forEach(result -> {
                        writer.printf("%-30s %12.2f %10.1f %12.1f %10s%n",
                                result.getConfiguration().getName(),
                                result.getMeanObjective(),
                                result.getMilpGapPercentage(),
                                result.getFeasibilityRate() * 100,
                                categorizePerformance(result));
                    });

            writer.println();
            writer.println("RECOMMENDATIONS:");
            writer.println("- For production use, select strategies with 'EXCELLENT' or 'GOOD' categories");
            writer.println("- Prioritize strategies with feasibility rates above 95%");
            writer.println("- Consider computational time requirements for real-time applications");
            writer.println("- Validate selected strategy on additional problem instances");
            writer.println();
            writer.println("MILP BENCHMARK INFORMATION:");
            writer.println("- Dataset: " + datasetName);
            writer.println("- MILP Objective: " + milpBenchmark);
            writer.println("- Based on MODEL column from research paper constraint penalties");
        }

        LoggingUtils.logInfo("Analysis report exported: " + reportFile.getAbsolutePath());
    }

    /**
     * Shutdown benchmark runner
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * Main method for running benchmarks from command line
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java BenchmarkRunner <data_path> [runs_per_strategy]");
            System.out.println("  data_path: Path to CSV data files (month1, month2, or month3)");
            System.out.println("  runs_per_strategy: Number of runs per strategy (default: 30)");
            return;
        }

        try {
            String dataPath = args[0];
            int runsPerStrategy = args.length >= 2 ? Integer.parseInt(args[1]) : 30;

            // Load problem instance
            LoggingUtils.logInfo("Loading problem instance from: " + dataPath);
            ProblemInstance instance = DataLoader.loadProblemInstance(dataPath);
            ValidationUtils.validateProblemInstance(instance);
            ValidationUtils.printProblemInstanceSummary(instance);

            // Create and run benchmark
            BenchmarkRunner runner = new BenchmarkRunner(instance);

            try {
                List<BenchmarkResult> results = runner.runComprehensiveBenchmark(runsPerStrategy);

                LoggingUtils.logInfo("\nBenchmark completed successfully!");
                LoggingUtils.logInfo("Total strategies tested: " + results.size());
                LoggingUtils.logInfo("Results exported to: " + runner.outputDirectory);
                LoggingUtils.logInfo("Dataset: " + runner.datasetName +
                        " (MILP benchmark: " + runner.getMILPBenchmark() + ")");

            } finally {
                runner.shutdown();
            }

        } catch (Exception e) {
            LoggingUtils.logError("Benchmark failed: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    // ============================================================================
    // INNER CLASSES
    // ============================================================================

    /**
     * Configuration for a benchmark strategy
     */
    public static class BenchmarkConfiguration {
        private String name;
        private String description;
        private HeuristicFactory heuristicFactory;

        public BenchmarkConfiguration(String name, String description, HeuristicFactory heuristicFactory) {
            this.name = name;
            this.description = description;
            this.heuristicFactory = heuristicFactory;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public HeuristicFactory getHeuristicFactory() {
            return heuristicFactory;
        }
    }

    /**
     * Factory interface for creating heuristic instances
     */
    @FunctionalInterface
    public interface HeuristicFactory {
        ConstructiveHeuristic create();
    }

    /**
     * Result of a single benchmark run
     */
    public static class BenchmarkRun {
        private int runId;
        private double objectiveValue;
        private long computationTime;
        private boolean feasible;
        private String errorMessage;

        public BenchmarkRun(int runId, double objectiveValue, long computationTime,
                boolean feasible, String errorMessage) {
            this.runId = runId;
            this.objectiveValue = objectiveValue;
            this.computationTime = computationTime;
            this.feasible = feasible;
            this.errorMessage = errorMessage;
        }

        // Getters
        public int getRunId() {
            return runId;
        }

        public double getObjectiveValue() {
            return objectiveValue;
        }

        public long getComputationTime() {
            return computationTime;
        }

        public boolean isFeasible() {
            return feasible;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isValid() {
            return objectiveValue < Double.MAX_VALUE;
        }
    }

    /**
     * Aggregated results for a benchmark configuration with corrected MILP
     * comparison
     */
    public static class BenchmarkResult {
        private BenchmarkConfiguration configuration;
        private List<BenchmarkRun> runs;
        private double meanObjective;
        private double stdDevObjective;
        private double bestObjective;
        private double worstObjective;
        private double meanComputationTime;
        private double feasibilityRate;
        private double milpGapPercentage;
        private double milpBenchmark;

        public BenchmarkResult(BenchmarkConfiguration configuration, List<BenchmarkRun> runs, double milpBenchmark) {
            this.configuration = configuration;
            this.runs = new ArrayList<>(runs);
            this.milpBenchmark = milpBenchmark;
            calculateStatistics();
        }

        private void calculateStatistics() {
            List<BenchmarkRun> validRuns = runs.stream()
                    .filter(BenchmarkRun::isValid)
                    .collect(Collectors.toList());

            if (validRuns.isEmpty()) {
                this.meanObjective = Double.MAX_VALUE;
                this.stdDevObjective = 0.0;
                this.bestObjective = Double.MAX_VALUE;
                this.worstObjective = Double.MAX_VALUE;
                this.meanComputationTime = 0.0;
                this.feasibilityRate = 0.0;
                this.milpGapPercentage = Double.MAX_VALUE;
                return;
            }

            // Objective statistics
            List<Double> objectives = validRuns.stream()
                    .map(BenchmarkRun::getObjectiveValue)
                    .collect(Collectors.toList());

            this.meanObjective = objectives.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            this.bestObjective = objectives.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            this.worstObjective = objectives.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

            if (objectives.size() > 1) {
                double variance = objectives.stream()
                        .mapToDouble(x -> Math.pow(x - meanObjective, 2))
                        .average()
                        .orElse(0.0);
                this.stdDevObjective = Math.sqrt(variance);
            } else {
                this.stdDevObjective = 0.0;
            }

            // Computation time
            this.meanComputationTime = runs.stream()
                    .mapToLong(BenchmarkRun::getComputationTime)
                    .average()
                    .orElse(0.0);

            // Feasibility rate
            long feasibleCount = runs.stream()
                    .mapToLong(run -> run.isFeasible() ? 1 : 0)
                    .sum();
            this.feasibilityRate = (double) feasibleCount / runs.size();

            // MILP gap with corrected benchmark
            if (meanObjective < Double.MAX_VALUE && milpBenchmark > 0) {
                this.milpGapPercentage = ((meanObjective - milpBenchmark) / milpBenchmark) * 100;
            } else {
                this.milpGapPercentage = Double.MAX_VALUE;
            }
        }

        // Getters
        public BenchmarkConfiguration getConfiguration() {
            return configuration;
        }

        public List<BenchmarkRun> getRuns() {
            return new ArrayList<>(runs);
        }

        public double getMeanObjective() {
            return meanObjective;
        }

        public double getStdDevObjective() {
            return stdDevObjective;
        }

        public double getBestObjective() {
            return bestObjective;
        }

        public double getWorstObjective() {
            return worstObjective;
        }

        public double getMeanComputationTime() {
            return meanComputationTime;
        }

        public double getFeasibilityRate() {
            return feasibilityRate;
        }

        public double getMilpGapPercentage() {
            return milpGapPercentage;
        }

        public double getMilpBenchmark() {
            return milpBenchmark;
        }

        public int getTotalRuns() {
            return runs.size();
        }

        public int getValidRunCount() {
            return (int) runs.stream().filter(BenchmarkRun::isValid).count();
        }
    }
}