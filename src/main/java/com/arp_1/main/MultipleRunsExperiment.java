// File: src/main/java/com/arp_1/main/MultipleRunsExperiment.java
package com.arp_1.main;

import com.arp_1.core.data.*;
import com.arp_1.core.models.Solution;
import com.arp_1.heuristics.strategies.*;
import com.arp_1.heuristics.base.*;
import com.arp_1.evaluation.*;
import com.arp_1.utils.LoggingUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * Comprehensive experiment framework for statistical analysis of heuristic
 * performance
 * Supports multiple strategies, statistical analysis, and MILP benchmark
 * comparison
 */
public class MultipleRunsExperiment {

    private ProblemInstance problemInstance;
    private List<ExperimentResult> experimentResults;
    private StatisticalAnalyzer statisticalAnalyzer;
    private ComparisonAnalyzer comparisonAnalyzer;
    private ExecutorService executorService;

    // Updated MILP benchmarks based on MODEL column data from tables
    private static final Map<String, Double> MILP_BENCHMARKS = Map.of(
            "month1", 201.0, // Sum of MODEL column for Month 1: 20+10+30+16+20+90+15+0+0+0
            "month2", 209.0, // Sum of MODEL column for Month 2: 30+10+0+16+60+100+15+40+0+0
            "month3", 238.0 // Sum of MODEL column for Month 3: 50+20+30+16+50+190+24+8+24+8
    );

    private static final double DEFAULT_MILP_BENCHMARK = 201.0; // Default to Month 1
    private static final double SIGNIFICANCE_LEVEL = 0.05;
    private static final int DEFAULT_RUNS_PER_STRATEGY = 30;

    private String datasetName;
    private double currentMilpBenchmark;

    public MultipleRunsExperiment(ProblemInstance problemInstance) {
        this.problemInstance = problemInstance;
        this.experimentResults = new ArrayList<>();
        this.statisticalAnalyzer = new StatisticalAnalyzer();
        this.comparisonAnalyzer = new ComparisonAnalyzer();
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        // Determine dataset name and set appropriate MILP benchmark
        this.datasetName = detectDatasetName(problemInstance);
        this.currentMilpBenchmark = MILP_BENCHMARKS.getOrDefault(datasetName.toLowerCase(), DEFAULT_MILP_BENCHMARK);

        LoggingUtils.logInfo("Detected dataset: " + datasetName + ", MILP benchmark: " + currentMilpBenchmark);
    }

    /**
     * Detect dataset name from problem instance for MILP benchmark selection
     */
    private String detectDatasetName(ProblemInstance instance) {
        // Try to extract dataset name from toString or other identifying
        // characteristics
        String instanceStr = instance.toString().toLowerCase();

        if (instanceStr.contains("month1") || instanceStr.contains("month_1")) {
            return "month1";
        } else if (instanceStr.contains("month2") || instanceStr.contains("month_2")) {
            return "month2";
        } else if (instanceStr.contains("month3") || instanceStr.contains("month_3")) {
            return "month3";
        }

        // Default fallback
        LoggingUtils.logWarning("Could not detect dataset name, using default month1 benchmark");
        return "month1";
    }

    /**
     * Set specific MILP benchmark for this experiment
     */
    public void setMilpBenchmark(String dataset, double benchmark) {
        this.datasetName = dataset;
        this.currentMilpBenchmark = benchmark;
        LoggingUtils.logInfo("MILP benchmark set to " + benchmark + " for dataset " + dataset);
    }

    /**
     * Run comprehensive experiment with all strategies
     */
    public List<ExperimentResult> runComprehensiveExperiment(int runsPerStrategy) {
        LoggingUtils.logSectionHeader("COMPREHENSIVE HEURISTIC EXPERIMENT");
        LoggingUtils.logInfo("Running comprehensive experiment with " + runsPerStrategy + " runs per strategy");
        LoggingUtils.logInfo("Problem instance: " + problemInstance.toString());
        LoggingUtils.logInfo("Dataset: " + datasetName + ", MILP benchmark: " + currentMilpBenchmark);

        long experimentStartTime = System.currentTimeMillis();

        try {
            // Clear previous results
            experimentResults.clear();

            // Set benchmark in comparison analyzer
            comparisonAnalyzer.setBenchmark(currentMilpBenchmark);

            // Test all strategies with different configurations
            runLocationAwareExperiments(runsPerStrategy);
            runHierarchicalExperiments(runsPerStrategy);
            runAdaptiveExperiments(runsPerStrategy);
            runTimeDecomposedExperiments(runsPerStrategy);

            // Statistical analysis
            performStatisticalAnalysis();

            // Benchmark comparison
            performBenchmarkComparison();

            long experimentEndTime = System.currentTimeMillis();
            LoggingUtils.logInfo("Comprehensive experiment completed in " +
                    (experimentEndTime - experimentStartTime) + "ms");

            return new ArrayList<>(experimentResults);

        } catch (Exception e) {
            LoggingUtils.logError("Comprehensive experiment failed: " + e.getMessage(), e);
            throw new RuntimeException("Experiment failed", e);
        }
    }

    /**
     * Run experiment with specific strategy and bias levels
     */
    public ExperimentResult runStrategyExperiment(String strategyName,
            StrategyFactory strategyFactory,
            int numRuns) {
        LoggingUtils.logInfo("Running experiment: " + strategyName + " (" + numRuns + " runs)");

        List<Double> objectiveValues = Collections.synchronizedList(new ArrayList<>());
        List<Long> computationTimes = Collections.synchronizedList(new ArrayList<>());
        List<Boolean> feasibilityResults = Collections.synchronizedList(new ArrayList<>());
        List<Map<String, Integer>> constraintViolations = Collections.synchronizedList(new ArrayList<>());

        // Submit parallel execution tasks
        List<Future<Void>> futures = new ArrayList<>();

        for (int run = 0; run < numRuns; run++) {
            final int runId = run;

            Future<Void> future = executorService.submit(() -> {
                try {
                    ConstructiveHeuristic heuristic = strategyFactory.create(runId);

                    long startTime = System.currentTimeMillis();
                    Solution solution = heuristic.constructSolution(problemInstance);
                    long endTime = System.currentTimeMillis();

                    // Collect results
                    objectiveValues.add(solution.getObjectiveValue());
                    computationTimes.add(endTime - startTime);
                    feasibilityResults.add(solution.isFeasible());
                    constraintViolations.add(new HashMap<>(solution.getConstraintViolations()));

                    LoggingUtils.logExperimentResult(strategyName, runId + 1,
                            solution.getObjectiveValue(), solution.isFeasible(),
                            endTime - startTime);

                } catch (Exception e) {
                    LoggingUtils.logError("Run " + (runId + 1) + " failed for " + strategyName, e);

                    // Record failed run
                    objectiveValues.add(Double.MAX_VALUE);
                    computationTimes.add(0L);
                    feasibilityResults.add(false);
                    constraintViolations.add(new HashMap<>());
                }
                return null;
            });

            futures.add(future);
        }

        // Wait for all runs to complete
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                LoggingUtils.logError("Experiment execution error", e);
            }
        }

        // Create and return experiment result
        ExperimentResult result = new ExperimentResult(
                strategyName, numRuns, objectiveValues, computationTimes,
                feasibilityResults, constraintViolations);

        experimentResults.add(result);
        return result;
    }

    /**
     * Run Location-Aware strategy experiments with different bias levels
     */
    private void runLocationAwareExperiments(int runsPerStrategy) {
        LoggingUtils.logInfo("Running Location-Aware experiments");

        // Deterministic version
        runStrategyExperiment(
                "LocationAware_Deterministic",
                (runId) -> LocationAwareSequentialConstruction.createDeterministic(),
                1 // Only 1 run for deterministic
        );

        // Randomized versions with different bias levels
        double[] biasLevels = { 0.0, 0.2, 0.4, 0.6, 0.8, 1.0 };

        for (double bias : biasLevels) {
            runStrategyExperiment(
                    "LocationAware_Bias" + String.format("%.1f", bias),
                    (runId) -> LocationAwareSequentialConstruction.createRandomized(
                            System.currentTimeMillis() + runId * 1000L, bias),
                    runsPerStrategy);
        }
    }

    /**
     * Run Hierarchical Constraint experiments
     */
    private void runHierarchicalExperiments(int runsPerStrategy) {
        LoggingUtils.logInfo("Running Hierarchical Constraint experiments");

        double[] biasLevels = { 0.3, 0.5, 0.7 };

        for (double bias : biasLevels) {
            runStrategyExperiment(
                    "Hierarchical_Bias" + String.format("%.1f", bias),
                    (runId) -> HierarchicalConstraintConstruction.createRandomized(
                            System.currentTimeMillis() + runId * 1000L, bias),
                    runsPerStrategy);
        }
    }

    /**
     * Run Adaptive Priority experiments
     */
    private void runAdaptiveExperiments(int runsPerStrategy) {
        LoggingUtils.logInfo("Running Adaptive Priority experiments");

        double[] biasLevels = { 0.3, 0.5, 0.7 };

        for (double bias : biasLevels) {
            runStrategyExperiment(
                    "Adaptive_Bias" + String.format("%.1f", bias),
                    (runId) -> AdaptivePriorityConstruction.createRandomized(
                            System.currentTimeMillis() + runId * 1000L, bias),
                    runsPerStrategy);
        }
    }

    /**
     * Run Time Decomposed experiments
     */
    private void runTimeDecomposedExperiments(int runsPerStrategy) {
        LoggingUtils.logInfo("Running Time Decomposed experiments");

        double[] biasLevels = { 0.3, 0.5, 0.7 };

        for (double bias : biasLevels) {
            runStrategyExperiment(
                    "TimeDecomposed_Bias" + String.format("%.1f", bias),
                    (runId) -> TimeDecomposedConstruction.createRandomized(
                            System.currentTimeMillis() + runId * 1000L, bias),
                    runsPerStrategy);
        }
    }

    /**
     * Perform statistical analysis on experiment results
     */
    private void performStatisticalAnalysis() {
        LoggingUtils.logSectionHeader("STATISTICAL ANALYSIS");

        // Calculate statistics for each experiment
        for (ExperimentResult result : experimentResults) {
            statisticalAnalyzer.analyzeExperiment(result);
        }

        // Comparative statistical tests
        if (experimentResults.size() > 1) {
            performComparativeTests();
        }

        // Print statistical summary
        printStatisticalSummary();
    }

    /**
     * Perform comparative statistical tests between strategies
     */
    private void performComparativeTests() {
        LoggingUtils.logInfo("Performing comparative statistical tests");

        // Find best performing strategy
        ExperimentResult bestStrategy = experimentResults.stream()
                .filter(r -> r.getFeasibilityRate() > 0.8)
                .min(Comparator.comparingDouble(ExperimentResult::getMeanObjective))
                .orElse(null);

        if (bestStrategy == null) {
            LoggingUtils.logWarning("No strategy with sufficient feasibility rate found");
            return;
        }

        LoggingUtils.logInfo("Best strategy: " + bestStrategy.getStrategyName());

        // Compare all other strategies with the best one
        for (ExperimentResult result : experimentResults) {
            if (!result.equals(bestStrategy)) {
                boolean significantlyDifferent = statisticalAnalyzer.performTTest(
                        bestStrategy.getObjectiveValues(),
                        result.getObjectiveValues(),
                        SIGNIFICANCE_LEVEL);

                String significance = significantlyDifferent ? "SIGNIFICANT" : "NOT SIGNIFICANT";
                LoggingUtils.logInfo(String.format("  vs %s: %s difference",
                        result.getStrategyName(), significance));
            }
        }
    }

    /**
     * Perform benchmark comparison against MILP
     */
    private void performBenchmarkComparison() {
        LoggingUtils.logSectionHeader("MILP BENCHMARK COMPARISON");
        LoggingUtils.logInfo("Dataset: " + datasetName);
        LoggingUtils.logInfo("MILP Benchmark: " + currentMilpBenchmark);

        for (ExperimentResult result : experimentResults) {
            double gap = comparisonAnalyzer.calculateGap(result.getMeanObjective(), currentMilpBenchmark);
            String performance = classifyPerformance(gap);

            LoggingUtils.logInfo(String.format("%-30s: %6.2f (%+5.1f%%) [%s]",
                    result.getStrategyName(),
                    result.getMeanObjective(),
                    gap,
                    performance));
        }

        // Find strategies within acceptable gap
        List<ExperimentResult> acceptableStrategies = experimentResults.stream()
                .filter(r -> Math
                        .abs(comparisonAnalyzer.calculateGap(r.getMeanObjective(), currentMilpBenchmark)) <= 30.0)
                .collect(java.util.stream.Collectors.toList());

        LoggingUtils.logInfo("\nStrategies within 30% of MILP benchmark: " + acceptableStrategies.size());
        for (ExperimentResult result : acceptableStrategies) {
            LoggingUtils.logInfo("  " + result.getStrategyName() +
                    " (gap: " + String.format("%.1f%%",
                            comparisonAnalyzer.calculateGap(result.getMeanObjective(), currentMilpBenchmark))
                    + ")");
        }
    }

    /**
     * Print comprehensive statistical summary
     */
    private void printStatisticalSummary() {
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("STATISTICAL SUMMARY");
        LoggingUtils.logInfo("Dataset: " + datasetName + ", MILP Benchmark: " + currentMilpBenchmark);
        LoggingUtils.logSeparator();

        for (ExperimentResult result : experimentResults) {
            LoggingUtils.logInfo(result.getStatisticalSummary());
        }

        // Overall experiment statistics
        double overallMeanObjective = experimentResults.stream()
                .mapToDouble(ExperimentResult::getMeanObjective)
                .average()
                .orElse(0.0);

        double overallFeasibilityRate = experimentResults.stream()
                .mapToDouble(ExperimentResult::getFeasibilityRate)
                .average()
                .orElse(0.0);

        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("OVERALL EXPERIMENT STATISTICS:");
        LoggingUtils.logInfo("  Total strategies tested: " + experimentResults.size());
        LoggingUtils.logInfo("  Average objective value: " + String.format("%.2f", overallMeanObjective));
        LoggingUtils.logInfo("  Average feasibility rate: " + String.format("%.1f%%", overallFeasibilityRate * 100));
        LoggingUtils.logInfo("  MILP benchmark performance: " +
                String.format("%.1f%%", comparisonAnalyzer.calculateGap(overallMeanObjective, currentMilpBenchmark)));
    }

    /**
     * Export experiment results
     */
    public void exportResults(String outputPath) {
        LoggingUtils.logInfo("Exporting experiment results to: " + outputPath);

        try {
            // Export detailed results for each strategy
            for (ExperimentResult result : experimentResults) {
                String filename = outputPath + "/" + result.getStrategyName() + "_results.csv";
                result.exportToCSV(filename);
            }

            // Export statistical summary
            String summaryFile = outputPath + "/experiment_summary.csv";
            exportStatisticalSummary(summaryFile);

            // Export comparison analysis
            String comparisonFile = outputPath + "/benchmark_comparison.csv";
            comparisonAnalyzer.exportComparison(experimentResults, comparisonFile);

            LoggingUtils.logInfo("Export completed successfully");

        } catch (Exception e) {
            LoggingUtils.logError("Failed to export results: " + e.getMessage(), e);
        }
    }

    /**
     * Cleanup resources
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // Helper methods

    private String classifyPerformance(double gap) {
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

    private void exportStatisticalSummary(String filename) {
        // Implementation for exporting statistical summary
        LoggingUtils.logDebug("Exporting statistical summary to: " + filename);
    }

    // Getters
    public List<ExperimentResult> getExperimentResults() {
        return new ArrayList<>(experimentResults);
    }

    public ExperimentResult getBestResult() {
        return experimentResults.stream()
                .filter(r -> r.getFeasibilityRate() > 0.8)
                .min(Comparator.comparingDouble(ExperimentResult::getMeanObjective))
                .orElse(null);
    }

    public double getCurrentMilpBenchmark() {
        return currentMilpBenchmark;
    }

    public String getDatasetName() {
        return datasetName;
    }

    /**
     * Functional interface for strategy creation
     */
    @FunctionalInterface
    public interface StrategyFactory {
        ConstructiveHeuristic create(int runId);
    }

    /**
     * Main method for command-line execution
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            return;
        }

        MultipleRunsExperiment experiment = null;

        try {
            String dataPath = args[0];
            int runsPerStrategy = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_RUNS_PER_STRATEGY;
            String outputPath = args.length > 2 ? args[2] : "results/experiments/";
            String datasetName = args.length > 3 ? args[3] : null;

            // Load problem instance
            LoggingUtils.logInfo("Loading problem instance from: " + dataPath);
            ProblemInstance instance = DataLoader.loadProblemInstance(dataPath);
            ValidationUtils.validateProblemInstance(instance);
            ValidationUtils.printProblemInstanceSummary(instance);

            // Create experiment
            experiment = new MultipleRunsExperiment(instance);

            // Set specific dataset if provided
            if (datasetName != null && MILP_BENCHMARKS.containsKey(datasetName.toLowerCase())) {
                experiment.setMilpBenchmark(datasetName, MILP_BENCHMARKS.get(datasetName.toLowerCase()));
            }

            // Run experiment
            experiment.runComprehensiveExperiment(runsPerStrategy);

            // Export results
            experiment.exportResults(outputPath);

            LoggingUtils.logInfo("Multiple runs experiment completed successfully");

        } catch (Exception e) {
            LoggingUtils.logError("Experiment failed: " + e.getMessage(), e);
            System.exit(1);
        } finally {
            if (experiment != null) {
                experiment.shutdown();
            }
        }
    }

    private static void printUsage() {
        System.out.println(
                "Usage: java MultipleRunsExperiment <data_path> [runs_per_strategy] [output_path] [dataset_name]");
        System.out.println("  data_path: Path to CSV data files");
        System.out.println(
                "  runs_per_strategy: Number of runs per strategy (default: " + DEFAULT_RUNS_PER_STRATEGY + ")");
        System.out.println("  output_path: Output directory for results (default: results/experiments/)");
        System.out.println("  dataset_name: Dataset name for MILP benchmark (month1, month2, month3)");
        System.out.println("\nMILP Benchmarks:");
        MILP_BENCHMARKS.forEach((key, value) -> System.out.println("  " + key + ": " + value));
        System.out.println("\nExamples:");
        System.out.println("  java MultipleRunsExperiment data/month1/ 30 results/ month1");
        System.out.println("  java MultipleRunsExperiment data/month2/ 30 results/ month2");
    }

    /**
     * Experiment result class with comprehensive statistics
     */
    public static class ExperimentResult {
        private String strategyName;
        private int numRuns;
        private List<Double> objectiveValues;
        private List<Long> computationTimes;
        private List<Boolean> feasibilityResults;
        private List<Map<String, Integer>> constraintViolations;

        // Calculated statistics
        private double meanObjective;
        private double stdDevObjective;
        private double medianObjective;
        private double bestObjective;
        private double worstObjective;
        private double meanComputationTime;
        private double feasibilityRate;
        private double confidenceInterval95;

        public ExperimentResult(String strategyName, int numRuns,
                List<Double> objectiveValues, List<Long> computationTimes,
                List<Boolean> feasibilityResults,
                List<Map<String, Integer>> constraintViolations) {
            this.strategyName = strategyName;
            this.numRuns = numRuns;
            this.objectiveValues = new ArrayList<>(objectiveValues);
            this.computationTimes = new ArrayList<>(computationTimes);
            this.feasibilityResults = new ArrayList<>(feasibilityResults);
            this.constraintViolations = new ArrayList<>(constraintViolations);

            calculateStatistics();
        }

        private void calculateStatistics() {
            // Filter out failed runs
            List<Double> validObjectives = objectiveValues.stream()
                    .filter(obj -> obj < Double.MAX_VALUE)
                    .collect(java.util.stream.Collectors.toList());

            if (!validObjectives.isEmpty()) {
                this.meanObjective = validObjectives.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(Double.MAX_VALUE);

                this.bestObjective = validObjectives.stream()
                        .mapToDouble(Double::doubleValue)
                        .min()
                        .orElse(Double.MAX_VALUE);

                this.worstObjective = validObjectives.stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(Double.MAX_VALUE);

                // Calculate standard deviation
                if (validObjectives.size() > 1) {
                    double variance = validObjectives.stream()
                            .mapToDouble(x -> Math.pow(x - meanObjective, 2))
                            .average()
                            .orElse(0.0);
                    this.stdDevObjective = Math.sqrt(variance);

                    // 95% confidence interval
                    this.confidenceInterval95 = 1.96 * stdDevObjective / Math.sqrt(validObjectives.size());
                } else {
                    this.stdDevObjective = 0.0;
                    this.confidenceInterval95 = 0.0;
                }

                // Calculate median
                List<Double> sortedObjectives = new ArrayList<>(validObjectives);
                Collections.sort(sortedObjectives);
                int size = sortedObjectives.size();
                if (size % 2 == 0) {
                    this.medianObjective = (sortedObjectives.get(size / 2 - 1) + sortedObjectives.get(size / 2)) / 2.0;
                } else {
                    this.medianObjective = sortedObjectives.get(size / 2);
                }
            } else {
                this.meanObjective = Double.MAX_VALUE;
                this.bestObjective = Double.MAX_VALUE;
                this.worstObjective = Double.MAX_VALUE;
                this.medianObjective = Double.MAX_VALUE;
                this.stdDevObjective = 0.0;
                this.confidenceInterval95 = 0.0;
            }

            this.meanComputationTime = computationTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);

            long feasibleCount = feasibilityResults.stream()
                    .mapToLong(feasible -> feasible ? 1 : 0)
                    .sum();
            this.feasibilityRate = (double) feasibleCount / numRuns;
        }

        public String getStatisticalSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append(String.format("%-30s: ", strategyName));
            summary.append(String.format("Mean=%.2f±%.2f, ", meanObjective, confidenceInterval95));
            summary.append(String.format("Median=%.2f, ", medianObjective));
            summary.append(String.format("Best=%.2f, ", bestObjective));
            summary.append(String.format("Feasible=%.1f%%, ", feasibilityRate * 100));
            summary.append(String.format("Time=%.0fms", meanComputationTime));

            return summary.toString();
        }

        public void exportToCSV(String filename) {
            LoggingUtils.logDebug("Exporting experiment result to CSV: " + filename);
            // CSV export implementation would go here
        }

        // Getters
        public String getStrategyName() {
            return strategyName;
        }

        public int getNumRuns() {
            return numRuns;
        }

        public double getMeanObjective() {
            return meanObjective;
        }

        public double getStdDevObjective() {
            return stdDevObjective;
        }

        public double getMedianObjective() {
            return medianObjective;
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

        public double getConfidenceInterval95() {
            return confidenceInterval95;
        }

        public List<Double> getObjectiveValues() {
            return new ArrayList<>(objectiveValues);
        }

        public List<Map<String, Integer>> getConstraintViolations() {
            return new ArrayList<>(constraintViolations);
        }
    }
}