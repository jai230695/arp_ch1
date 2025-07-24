// File: src/main/java/com/arp/main/BenchmarkRunner.java
package com.arp_1.main;

import com.arp_1.core.data.*;
import com.arp_1.core.models.Solution;
import com.arp_1.heuristics.base.ConstructiveHeuristic;
import com.arp_1.heuristics.strategies.*;
import com.arp_1.evaluation.*;
import com.arp_1.utils.LoggingUtils;
import java.util.*;
import java.util.concurrent.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Comprehensive benchmark runner for ARP heuristics
 * Supports multiple datasets, systematic parameter testing, and performance
 * comparison
 */
public class BenchmarkRunner {

    private List<BenchmarkDataset> datasets;
    private Map<String, BenchmarkConfiguration> configurations;
    private List<BenchmarkResult> results;
    private FairnessAnalyzer fairnessAnalyzer;
    private ComparisonAnalyzer comparisonAnalyzer;
    private PerformanceMetrics performanceMetrics;
    private ExecutorService executorService;

    // Benchmark constants
    private static final double MILP_BENCHMARK = 70.0;
    private static final int DEFAULT_RUNS_PER_CONFIG = 30;
    private static final long TIMEOUT_MINUTES = 10;

    public BenchmarkRunner() {
        this.datasets = new ArrayList<>();
        this.configurations = new HashMap<>();
        this.results = new ArrayList<>();
        this.fairnessAnalyzer = new FairnessAnalyzer();
        this.comparisonAnalyzer = new ComparisonAnalyzer();
        this.performanceMetrics = new PerformanceMetrics();
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        setupDefaultConfigurations();
    }

    /**
     * Load benchmark datasets from directory
     */
    public void loadDatasets(String datasetsPath) {
        LoggingUtils.logSectionHeader("LOADING BENCHMARK DATASETS");
        LoggingUtils.logInfo("Datasets path: " + datasetsPath);

        try {
            File datasetsDir = new File(datasetsPath);
            if (!datasetsDir.exists() || !datasetsDir.isDirectory()) {
                throw new IllegalArgumentException("Datasets path does not exist: " + datasetsPath);
            }

            datasets.clear();

            // Scan for dataset directories
            File[] subdirs = datasetsDir.listFiles(File::isDirectory);
            if (subdirs == null) {
                throw new RuntimeException("No subdirectories found in: " + datasetsPath);
            }

            for (File subdir : subdirs) {
                try {
                    LoggingUtils.logInfo("Loading dataset: " + subdir.getName());

                    ProblemInstance instance = DataLoader.loadProblemInstance(subdir.getAbsolutePath());
                    ValidationUtils.validateProblemInstance(instance);

                    BenchmarkDataset dataset = new BenchmarkDataset(
                            subdir.getName(),
                            subdir.getAbsolutePath(),
                            instance);

                    datasets.add(dataset);
                    LoggingUtils.logInfo("Successfully loaded dataset: " + subdir.getName() +
                            " (" + instance.getAnaesthetists().size() + " anaesthetists, " +
                            instance.getAllWorkstations().size() + " workstations)");

                } catch (Exception e) {
                    LoggingUtils.logWarning("Failed to load dataset " + subdir.getName() + ": " + e.getMessage());
                }
            }

            LoggingUtils.logInfo("Loaded " + datasets.size() + " benchmark datasets");

        } catch (Exception e) {
            LoggingUtils.logError("Failed to load datasets: " + e.getMessage(), e);
            throw new RuntimeException("Dataset loading failed", e);
        }
    }

    /**
     * Run comprehensive benchmark across all datasets and configurations
     */
    public List<BenchmarkResult> runComprehensiveBenchmark() {
        LoggingUtils.logSectionHeader("COMPREHENSIVE BENCHMARK EXECUTION");
        LoggingUtils.logInfo("Datasets: " + datasets.size());
        LoggingUtils.logInfo("Configurations: " + configurations.size());

        long benchmarkStartTime = System.currentTimeMillis();

        try {
            results.clear();

            int totalExperiments = datasets.size() * configurations.size();
            int currentExperiment = 0;

            for (BenchmarkDataset dataset : datasets) {
                LoggingUtils.logInfo("Benchmarking dataset: " + dataset.getName());

                for (BenchmarkConfiguration config : configurations.values()) {
                    currentExperiment++;
                    LoggingUtils.logProgress("Benchmark", currentExperiment, totalExperiments);

                    BenchmarkResult result = runSingleBenchmark(dataset, config);
                    results.add(result);
                }
            }

            // Analyze results
            analyzeBenchmarkResults();

            long benchmarkEndTime = System.currentTimeMillis();
            LoggingUtils.logInfo("Comprehensive benchmark completed in " +
                    (benchmarkEndTime - benchmarkStartTime) + "ms");

            return new ArrayList<>(results);

        } catch (Exception e) {
            LoggingUtils.logError("Benchmark execution failed: " + e.getMessage(), e);
            throw new RuntimeException("Benchmark failed", e);
        }
    }

    /**
     * Run benchmark for specific dataset and configuration
     */
    public BenchmarkResult runSingleBenchmark(BenchmarkDataset dataset, BenchmarkConfiguration config) {
        LoggingUtils.logInfo("Running benchmark: " + dataset.getName() + " with " + config.getName());

        List<Solution> solutions = Collections.synchronizedList(new ArrayList<>());
        List<Long> executionTimes = Collections.synchronizedList(new ArrayList<>());
        List<Future<Void>> futures = new ArrayList<>();

        // Execute multiple runs in parallel
        for (int run = 0; run < config.getNumRuns(); run++) {
            final int runId = run;

            Future<Void> future = executorService.submit(() -> {
                try {
                    // Create strategy instance
                    ConstructiveHeuristic strategy = config.getStrategyFactory().create(runId);

                    // Execute with timeout
                    long startTime = System.currentTimeMillis();

                    Future<Solution> solutionFuture = executorService.submit(() -> {
                        return strategy.constructSolution(dataset.getInstance());
                    });

                    Solution solution = solutionFuture.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
                    long endTime = System.currentTimeMillis();

                    solutions.add(solution);
                    executionTimes.add(endTime - startTime);

                } catch (TimeoutException e) {
                    LoggingUtils.logWarning("Run " + (runId + 1) + " timed out for " + config.getName());
                    solutions.add(createTimeoutSolution());
                    executionTimes.add(TIMEOUT_MINUTES * 60 * 1000L);

                } catch (Exception e) {
                    LoggingUtils.logError("Run " + (runId + 1) + " failed for " + config.getName(), e);
                    solutions.add(createFailedSolution());
                    executionTimes.add(0L);
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
                LoggingUtils.logError("Benchmark execution error", e);
            }
        }

        // Create benchmark result
        BenchmarkResult result = new BenchmarkResult(
                dataset.getName(),
                config.getName(),
                config.getNumRuns(),
                solutions,
                executionTimes,
                dataset.getInstance());

        LoggingUtils.logInfo("Benchmark completed: " + result.getSummary());

        return result;
    }

    /**
     * Run scalability analysis across different problem sizes
     */
    public List<BenchmarkResult> runScalabilityAnalysis() {
        LoggingUtils.logSectionHeader("SCALABILITY ANALYSIS");

        List<BenchmarkResult> scalabilityResults = new ArrayList<>();

        // Sort datasets by size (number of anaesthetists)
        List<BenchmarkDataset> sortedDatasets = new ArrayList<>(datasets);
        sortedDatasets.sort(Comparator.comparingInt(d -> d.getInstance().getAnaesthetists().size()));

        // Test best performing configuration on all dataset sizes
        BenchmarkConfiguration bestConfig = findBestConfiguration();
        if (bestConfig == null) {
            LoggingUtils.logWarning("No benchmark results available for scalability analysis");
            return scalabilityResults;
        }

        LoggingUtils.logInfo("Testing scalability with configuration: " + bestConfig.getName());

        for (BenchmarkDataset dataset : sortedDatasets) {
            BenchmarkResult result = runSingleBenchmark(dataset, bestConfig);
            scalabilityResults.add(result);

            LoggingUtils.logInfo(String.format("Dataset %s (size: %d): %.2f objective, %.1f%% feasible",
                    dataset.getName(),
                    dataset.getInstance().getAnaesthetists().size(),
                    result.getMeanObjective(),
                    result.getFeasibilityRate() * 100));
        }

        return scalabilityResults;
    }

    /**
     * Run parameter sensitivity analysis
     */
    public Map<String, List<BenchmarkResult>> runParameterSensitivityAnalysis() {
        LoggingUtils.logSectionHeader("PARAMETER SENSITIVITY ANALYSIS");

        Map<String, List<BenchmarkResult>> sensitivityResults = new HashMap<>();

        // Test different bias levels for randomized strategies
        double[] biasLevels = { 0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0 };

        if (!datasets.isEmpty()) {
            BenchmarkDataset testDataset = datasets.get(0); // Use first dataset for sensitivity

            LoggingUtils.logInfo("Testing bias sensitivity on dataset: " + testDataset.getName());

            List<BenchmarkResult> biasResults = new ArrayList<>();

            for (double bias : biasLevels) {
                BenchmarkConfiguration config = new BenchmarkConfiguration(
                        "LocationAware_Bias" + String.format("%.1f", bias),
                        (runId) -> LocationAwareSequentialConstruction.createRandomized(
                                System.currentTimeMillis() + runId * 1000L, bias),
                        20 // Reduced runs for sensitivity analysis
                );

                BenchmarkResult result = runSingleBenchmark(testDataset, config);
                biasResults.add(result);

                LoggingUtils.logInfo(String.format("Bias %.1f: %.2f objective, %.1f%% feasible",
                        bias, result.getMeanObjective(), result.getFeasibilityRate() * 100));
            }

            sensitivityResults.put("bias_sensitivity", biasResults);
        }

        return sensitivityResults;
    }

    /**
     * Analyze benchmark results
     */
    private void analyzeBenchmarkResults() {
        LoggingUtils.logSectionHeader("BENCHMARK RESULTS ANALYSIS");

        if (results.isEmpty()) {
            LoggingUtils.logWarning("No benchmark results to analyze");
            return;
        }

        // Overall performance statistics
        printOverallStatistics();

        // Strategy comparison
        performStrategyComparison();

        // Dataset analysis
        performDatasetAnalysis();

        // MILP benchmark comparison
        performMILPComparison();

        // Fairness analysis
        performFairnessAnalysis();
    }

    /**
     * Print overall benchmark statistics
     */
    private void printOverallStatistics() {
        LoggingUtils.logInfo("OVERALL BENCHMARK STATISTICS");
        LoggingUtils.logSeparator();

        int totalRuns = results.stream().mapToInt(BenchmarkResult::getNumRuns).sum();
        double overallFeasibilityRate = results.stream()
                .mapToDouble(BenchmarkResult::getFeasibilityRate)
                .average()
                .orElse(0.0);

        double overallMeanObjective = results.stream()
                .mapToDouble(BenchmarkResult::getMeanObjective)
                .average()
                .orElse(0.0);

        double overallMeanTime = results.stream()
                .mapToDouble(BenchmarkResult::getMeanExecutionTime)
                .average()
                .orElse(0.0);

        LoggingUtils.logInfo("Total benchmark runs: " + totalRuns);
        LoggingUtils.logInfo("Overall feasibility rate: " + String.format("%.1f%%", overallFeasibilityRate * 100));
        LoggingUtils.logInfo("Overall mean objective: " + String.format("%.2f", overallMeanObjective));
        LoggingUtils.logInfo("Overall mean execution time: " + String.format("%.1f ms", overallMeanTime));
    }

    /**
     * Perform strategy comparison
     */
    private void performStrategyComparison() {
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("STRATEGY COMPARISON");
        LoggingUtils.logSeparator();

        // Group results by configuration
        Map<String, List<BenchmarkResult>> resultsByConfig = new HashMap<>();
        for (BenchmarkResult result : results) {
            resultsByConfig.computeIfAbsent(result.getConfigurationName(), k -> new ArrayList<>()).add(result);
        }

        // Calculate average performance for each configuration
        for (Map.Entry<String, List<BenchmarkResult>> entry : resultsByConfig.entrySet()) {
            String configName = entry.getKey();
            List<BenchmarkResult> configResults = entry.getValue();

            double avgObjective = configResults.stream()
                    .mapToDouble(BenchmarkResult::getMeanObjective)
                    .average()
                    .orElse(0.0);

            double avgFeasibility = configResults.stream()
                    .mapToDouble(BenchmarkResult::getFeasibilityRate)
                    .average()
                    .orElse(0.0);

            double avgTime = configResults.stream()
                    .mapToDouble(BenchmarkResult::getMeanExecutionTime)
                    .average()
                    .orElse(0.0);

            LoggingUtils.logInfo(String.format("%-30s: Obj=%.2f, Feas=%.1f%%, Time=%.0fms",
                    configName, avgObjective, avgFeasibility * 100, avgTime));
        }
    }

    /**
     * Perform dataset analysis
     */
    private void performDatasetAnalysis() {
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("DATASET ANALYSIS");
        LoggingUtils.logSeparator();

        // Group results by dataset
        Map<String, List<BenchmarkResult>> resultsByDataset = new HashMap<>();
        for (BenchmarkResult result : results) {
            resultsByDataset.computeIfAbsent(result.getDatasetName(), k -> new ArrayList<>()).add(result);
        }

        for (Map.Entry<String, List<BenchmarkResult>> entry : resultsByDataset.entrySet()) {
            String datasetName = entry.getKey();
            List<BenchmarkResult> datasetResults = entry.getValue();

            BenchmarkResult bestResult = datasetResults.stream()
                    .filter(r -> r.getFeasibilityRate() > 0.8)
                    .min(Comparator.comparingDouble(BenchmarkResult::getMeanObjective))
                    .orElse(datasetResults.stream()
                            .min(Comparator.comparingDouble(BenchmarkResult::getMeanObjective))
                            .orElse(null));

            if (bestResult != null) {
                LoggingUtils.logInfo(String.format("%-15s: Best=%.2f (%s), Avg=%.2f",
                        datasetName,
                        bestResult.getMeanObjective(),
                        bestResult.getConfigurationName(),
                        datasetResults.stream().mapToDouble(BenchmarkResult::getMeanObjective).average().orElse(0.0)));
            }
        }
    }

    /**
     * Perform MILP benchmark comparison
     */
    private void performMILPComparison() {
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("MILP BENCHMARK COMPARISON (Target: " + MILP_BENCHMARK + ")");
        LoggingUtils.logSeparator();

        comparisonAnalyzer.setBenchmark(MILP_BENCHMARK);

        for (BenchmarkResult result : results) {
            double gap = comparisonAnalyzer.calculateGap(result.getMeanObjective(), MILP_BENCHMARK);
            String performance = classifyMILPPerformance(gap);

            LoggingUtils.logInfo(String.format("%-40s: %6.2f (%+5.1f%%) [%s]",
                    result.getDatasetName() + "_" + result.getConfigurationName(),
                    result.getMeanObjective(),
                    gap,
                    performance));
        }

        // Summary statistics
        long excellentCount = results.stream()
                .mapToLong(
                        r -> Math.abs(comparisonAnalyzer.calculateGap(r.getMeanObjective(), MILP_BENCHMARK)) <= 10.0 ? 1
                                : 0)
                .sum();

        long acceptableCount = results.stream()
                .mapToLong(
                        r -> Math.abs(comparisonAnalyzer.calculateGap(r.getMeanObjective(), MILP_BENCHMARK)) <= 30.0 ? 1
                                : 0)
                .sum();

        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("MILP Comparison Summary:");
        LoggingUtils.logInfo("  Excellent (≤10% gap): " + excellentCount + "/" + results.size());
        LoggingUtils.logInfo("  Acceptable (≤30% gap): " + acceptableCount + "/" + results.size());
    }

    /**
     * Perform fairness analysis
     */
    private void performFairnessAnalysis() {
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("FAIRNESS ANALYSIS");
        LoggingUtils.logSeparator();

        for (BenchmarkResult result : results) {
            if (!result.getSolutions().isEmpty()) {
                Solution bestSolution = result.getBestSolution();
                if (bestSolution != null && bestSolution.isFeasible()) {

                    BenchmarkDataset dataset = datasets.stream()
                            .filter(d -> d.getName().equals(result.getDatasetName()))
                            .findFirst()
                            .orElse(null);

                    if (dataset != null) {
                        fairnessAnalyzer.analyzeSolution(bestSolution, dataset.getInstance());

                        // Use FairnessAnalyzer.FairnessMetrics instead of
                        // BenchmarkRunner.FairnessMetrics
                        FairnessAnalyzer.FairnessMetrics metrics = fairnessAnalyzer.calculateFairnessMetrics(
                                bestSolution,
                                dataset.getInstance());

                        LoggingUtils.logInfo(String.format("%-40s: Workload=%.3f, Weekend=%.3f, Overall=%.3f",
                                result.getDatasetName() + "_" + result.getConfigurationName(),
                                metrics.getWorkloadFairness(),
                                metrics.getWeekendFairness(),
                                metrics.getOverallFairness()));
                    }
                }
            }
        }
    }

    /**
     * Export comprehensive benchmark results
     */
    public void exportResults(String outputPath) {
        LoggingUtils.logSectionHeader("EXPORTING BENCHMARK RESULTS");
        LoggingUtils.logInfo("Output path: " + outputPath);

        try {
            // Create output directory
            Files.createDirectories(Paths.get(outputPath));

            // Export individual benchmark results
            for (BenchmarkResult result : results) {
                String filename = outputPath + "/" + result.getDatasetName() + "_" +
                        result.getConfigurationName() + "_benchmark.csv";
                result.exportToCSV(filename);
            }

            // Export summary report
            String summaryFile = outputPath + "/benchmark_summary.csv";
            exportBenchmarkSummary(summaryFile);

            // Export strategy comparison
            String comparisonFile = outputPath + "/strategy_comparison.csv";
            exportStrategyComparison(comparisonFile);

            // Export scalability analysis if available
            List<BenchmarkResult> scalabilityResults = runScalabilityAnalysis();
            if (!scalabilityResults.isEmpty()) {
                String scalabilityFile = outputPath + "/scalability_analysis.csv";
                exportScalabilityAnalysis(scalabilityResults, scalabilityFile);
            }

            // Export parameter sensitivity if available
            Map<String, List<BenchmarkResult>> sensitivityResults = runParameterSensitivityAnalysis();
            for (Map.Entry<String, List<BenchmarkResult>> entry : sensitivityResults.entrySet()) {
                String sensitivityFile = outputPath + "/" + entry.getKey() + "_analysis.csv";
                exportSensitivityAnalysis(entry.getValue(), sensitivityFile);
            }

            LoggingUtils.logInfo("Benchmark results exported successfully");

        } catch (Exception e) {
            LoggingUtils.logError("Failed to export benchmark results: " + e.getMessage(), e);
        }
    }

    /**
     * Setup default benchmark configurations
     */
    private void setupDefaultConfigurations() {
        // Location-Aware configurations
        configurations.put("LocationAware_Det", new BenchmarkConfiguration(
                "LocationAware_Deterministic",
                (runId) -> LocationAwareSequentialConstruction.createDeterministic(),
                1));

        configurations.put("LocationAware_R05", new BenchmarkConfiguration(
                "LocationAware_Random_0.5",
                (runId) -> LocationAwareSequentialConstruction.createRandomized(
                        System.currentTimeMillis() + runId * 1000L, 0.5),
                DEFAULT_RUNS_PER_CONFIG));

        configurations.put("LocationAware_R07", new BenchmarkConfiguration(
                "LocationAware_Random_0.7",
                (runId) -> LocationAwareSequentialConstruction.createRandomized(
                        System.currentTimeMillis() + runId * 1000L, 0.7),
                DEFAULT_RUNS_PER_CONFIG));

        // Hierarchical configurations
        configurations.put("Hierarchical_R05", new BenchmarkConfiguration(
                "Hierarchical_Random_0.5",
                (runId) -> HierarchicalConstraintConstruction.createRandomized(
                        System.currentTimeMillis() + runId * 1000L, 0.5),
                DEFAULT_RUNS_PER_CONFIG));

        // Adaptive configurations
        configurations.put("Adaptive_R05", new BenchmarkConfiguration(
                "Adaptive_Random_0.5",
                (runId) -> AdaptivePriorityConstruction.createRandomized(
                        System.currentTimeMillis() + runId * 1000L, 0.5),
                DEFAULT_RUNS_PER_CONFIG));

        // Time Decomposed configurations
        configurations.put("TimeDecomposed_R05", new BenchmarkConfiguration(
                "TimeDecomposed_Random_0.5",
                (runId) -> TimeDecomposedConstruction.createRandomized(
                        System.currentTimeMillis() + runId * 1000L, 0.5),
                DEFAULT_RUNS_PER_CONFIG));
    }

    /**
     * Find best performing configuration from current results
     */
    private BenchmarkConfiguration findBestConfiguration() {
        if (results.isEmpty()) {
            return configurations.values().iterator().next(); // Return first config as default
        }

        BenchmarkResult bestResult = results.stream()
                .filter(r -> r.getFeasibilityRate() > 0.8)
                .min(Comparator.comparingDouble(BenchmarkResult::getMeanObjective))
                .orElse(null);

        if (bestResult != null) {
            return configurations.get(bestResult.getConfigurationName());
        }

        return configurations.values().iterator().next();
    }

    /**
     * Create solution for timeout cases
     */
    private Solution createTimeoutSolution() {
        Solution solution = new Solution();
        solution.setObjectiveValue(Double.MAX_VALUE);
        solution.setFeasible(false);
        solution.setConstructionMethod("TIMEOUT");
        solution.setComputationTime(TIMEOUT_MINUTES * 60 * 1000L);
        return solution;
    }

    /**
     * Create solution for failed cases
     */
    private Solution createFailedSolution() {
        Solution solution = new Solution();
        solution.setObjectiveValue(Double.MAX_VALUE);
        solution.setFeasible(false);
        solution.setConstructionMethod("FAILED");
        solution.setComputationTime(0L);
        return solution;
    }

    /**
     * Classify MILP performance
     */
    private String classifyMILPPerformance(double gap) {
        double absGap = Math.abs(gap);
        if (absGap <= 5.0)
            return "EXCELLENT";
        if (absGap <= 10.0)
            return "VERY_GOOD";
        if (absGap <= 20.0)
            return "GOOD";
        if (absGap <= 30.0)
            return "ACCEPTABLE";
        if (absGap <= 50.0)
            return "POOR";
        return "UNACCEPTABLE";
    }

    /**
     * Export benchmark summary
     */
    private void exportBenchmarkSummary(String filename) {
        LoggingUtils.logDebug("Exporting benchmark summary to: " + filename);
        // Implementation for CSV export would go here
    }

    /**
     * Export strategy comparison
     */
    private void exportStrategyComparison(String filename) {
        LoggingUtils.logDebug("Exporting strategy comparison to: " + filename);
        // Implementation for CSV export would go here
    }

    /**
     * Export scalability analysis
     */
    private void exportScalabilityAnalysis(List<BenchmarkResult> scalabilityResults, String filename) {
        LoggingUtils.logDebug("Exporting scalability analysis to: " + filename);
        // Implementation for CSV export would go here
    }

    /**
     * Export sensitivity analysis
     */
    private void exportSensitivityAnalysis(List<BenchmarkResult> sensitivityResults, String filename) {
        LoggingUtils.logDebug("Exporting sensitivity analysis to: " + filename);
        // Implementation for CSV export would go here
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

    // Getters
    public List<BenchmarkDataset> getDatasets() {
        return new ArrayList<>(datasets);
    }

    public List<BenchmarkResult> getResults() {
        return new ArrayList<>(results);
    }

    public Map<String, BenchmarkConfiguration> getConfigurations() {
        return new HashMap<>(configurations);
    }

    /**
     * Main method for command-line execution
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            return;
        }

        BenchmarkRunner runner = null;

        try {
            String datasetsPath = args[0];
            String outputPath = args.length > 1 ? args[1] : "results/benchmarks/";
            String analysisType = args.length > 2 ? args[2].toLowerCase() : "comprehensive";

            // Create benchmark runner
            runner = new BenchmarkRunner();

            // Load datasets
            runner.loadDatasets(datasetsPath);

            // Run analysis based on type
            switch (analysisType) {
                case "comprehensive":
                    runner.runComprehensiveBenchmark();
                    break;

                case "scalability":
                    runner.runScalabilityAnalysis();
                    break;

                case "sensitivity":
                    runner.runParameterSensitivityAnalysis();
                    break;

                default:
                    LoggingUtils.logWarning(
                            "Unknown analysis type: " + analysisType + ". Running comprehensive benchmark.");
                    runner.runComprehensiveBenchmark();
                    break;
            }

            // Export results
            runner.exportResults(outputPath);

            LoggingUtils.logInfo("Benchmark execution completed successfully");

        } catch (Exception e) {
            LoggingUtils.logError("Benchmark execution failed: " + e.getMessage(), e);
            System.exit(1);
        } finally {
            if (runner != null) {
                runner.shutdown();
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java BenchmarkRunner <datasets_path> [output_path] [analysis_type]");
        System.out.println("  datasets_path: Path to directory containing dataset subdirectories");
        System.out.println("  output_path: Output directory for results (default: results/benchmarks/)");
        System.out.println(
                "  analysis_type: Type of analysis - comprehensive|scalability|sensitivity (default: comprehensive)");
        System.out.println("\nExamples:");
        System.out.println("  java BenchmarkRunner data/ results/benchmarks/");
        System.out.println("  java BenchmarkRunner data/ results/ scalability");
        System.out.println("  java BenchmarkRunner data/ results/ sensitivity");
    }

    /**
     * Benchmark dataset class
     */
    public static class BenchmarkDataset {
        private String name;
        private String path;
        private ProblemInstance instance;

        public BenchmarkDataset(String name, String path, ProblemInstance instance) {
            this.name = name;
            this.path = path;
            this.instance = instance;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public ProblemInstance getInstance() {
            return instance;
        }

        @Override
        public String toString() {
            return String.format("Dataset{name='%s', anaesthetists=%d, workstations=%d}",
                    name, instance.getAnaesthetists().size(), instance.getAllWorkstations().size());
        }
    }

    /**
     * Benchmark configuration class
     */
    public static class BenchmarkConfiguration {
        private String name;
        private StrategyFactory strategyFactory;
        private int numRuns;

        public BenchmarkConfiguration(String name, StrategyFactory strategyFactory, int numRuns) {
            this.name = name;
            this.strategyFactory = strategyFactory;
            this.numRuns = numRuns;
        }

        public String getName() {
            return name;
        }

        public StrategyFactory getStrategyFactory() {
            return strategyFactory;
        }

        public int getNumRuns() {
            return numRuns;
        }

        @Override
        public String toString() {
            return String.format("Config{name='%s', runs=%d}", name, numRuns);
        }
    }

    /**
     * Strategy factory interface
     */
    @FunctionalInterface
    public interface StrategyFactory {
        ConstructiveHeuristic create(int runId);
    }

    /**
     * Benchmark result class
     */
    public static class BenchmarkResult {
        private String datasetName;
        private String configurationName;
        private int numRuns;
        private List<Solution> solutions;
        private List<Long> executionTimes;
        private ProblemInstance problemInstance;

        // Calculated statistics
        private double meanObjective;
        private double stdDevObjective;
        private double bestObjective;
        private double worstObjective;
        private double medianObjective;
        private double meanExecutionTime;
        private double feasibilityRate;
        private double confidenceInterval95;

        public BenchmarkResult(String datasetName, String configurationName, int numRuns,
                List<Solution> solutions, List<Long> executionTimes,
                ProblemInstance problemInstance) {
            this.datasetName = datasetName;
            this.configurationName = configurationName;
            this.numRuns = numRuns;
            this.solutions = new ArrayList<>(solutions);
            this.executionTimes = new ArrayList<>(executionTimes);
            this.problemInstance = problemInstance;

            calculateStatistics();
        }

        private void calculateStatistics() {
            // Filter valid solutions
            List<Double> validObjectives = solutions.stream()
                    .filter(s -> s.getObjectiveValue() < Double.MAX_VALUE)
                    .map(Solution::getObjectiveValue)
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

                // Calculate median
                List<Double> sortedObjectives = new ArrayList<>(validObjectives);
                Collections.sort(sortedObjectives);
                int size = sortedObjectives.size();
                if (size % 2 == 0) {
                    this.medianObjective = (sortedObjectives.get(size / 2 - 1) + sortedObjectives.get(size / 2)) / 2.0;
                } else {
                    this.medianObjective = sortedObjectives.get(size / 2);
                }

                // Calculate standard deviation and confidence interval
                if (validObjectives.size() > 1) {
                    double variance = validObjectives.stream()
                            .mapToDouble(x -> Math.pow(x - meanObjective, 2))
                            .average()
                            .orElse(0.0);
                    this.stdDevObjective = Math.sqrt(variance);
                    this.confidenceInterval95 = 1.96 * stdDevObjective / Math.sqrt(validObjectives.size());
                } else {
                    this.stdDevObjective = 0.0;
                    this.confidenceInterval95 = 0.0;
                }
            } else {
                this.meanObjective = Double.MAX_VALUE;
                this.bestObjective = Double.MAX_VALUE;
                this.worstObjective = Double.MAX_VALUE;
                this.medianObjective = Double.MAX_VALUE;
                this.stdDevObjective = 0.0;
                this.confidenceInterval95 = 0.0;
            }

            // Execution time statistics
            this.meanExecutionTime = executionTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);

            // Feasibility rate
            long feasibleCount = solutions.stream()
                    .mapToLong(s -> s.isFeasible() ? 1 : 0)
                    .sum();
            this.feasibilityRate = (double) feasibleCount / numRuns;
        }

        public Solution getBestSolution() {
            return solutions.stream()
                    .filter(s -> s.getObjectiveValue() < Double.MAX_VALUE)
                    .filter(Solution::isFeasible)
                    .min(Comparator.comparingDouble(Solution::getObjectiveValue))
                    .orElse(solutions.stream()
                            .filter(s -> s.getObjectiveValue() < Double.MAX_VALUE)
                            .min(Comparator.comparingDouble(Solution::getObjectiveValue))
                            .orElse(null));
        }

        public String getSummary() {
            return String.format("%s_%s: Mean=%.2f±%.2f, Best=%.2f, Feasible=%.1f%%, Time=%.0fms",
                    datasetName, configurationName, meanObjective, confidenceInterval95,
                    bestObjective, feasibilityRate * 100, meanExecutionTime);
        }

        public void exportToCSV(String filename) {
            LoggingUtils.logDebug("Exporting benchmark result to CSV: " + filename);
            // Implementation for detailed CSV export would go here
            // Would include all run details, statistics, constraint violations, etc.
        }

        // Getters
        public String getDatasetName() {
            return datasetName;
        }

        public String getConfigurationName() {
            return configurationName;
        }

        public int getNumRuns() {
            return numRuns;
        }

        public List<Solution> getSolutions() {
            return new ArrayList<>(solutions);
        }

        public List<Long> getExecutionTimes() {
            return new ArrayList<>(executionTimes);
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

        public double getMedianObjective() {
            return medianObjective;
        }

        public double getMeanExecutionTime() {
            return meanExecutionTime;
        }

        public double getFeasibilityRate() {
            return feasibilityRate;
        }

        public double getConfidenceInterval95() {
            return confidenceInterval95;
        }

        public ProblemInstance getProblemInstance() {
            return problemInstance;
        }
    }

    /**
     * Fairness metrics class
     */
    public static class FairnessMetrics {
        private double workloadFairness;
        private double weekendFairness;
        private double preHolidayFairness;
        private double overallFairness;

        public FairnessMetrics(double workloadFairness, double weekendFairness,
                double preHolidayFairness, double overallFairness) {
            this.workloadFairness = workloadFairness;
            this.weekendFairness = weekendFairness;
            this.preHolidayFairness = preHolidayFairness;
            this.overallFairness = overallFairness;
        }

        public double getWorkloadFairness() {
            return workloadFairness;
        }

        public double getWeekendFairness() {
            return weekendFairness;
        }

        public double getPreHolidayFairness() {
            return preHolidayFairness;
        }

        public double getOverallFairness() {
            return overallFairness;
        }

        @Override
        public String toString() {
            return String.format("FairnessMetrics{workload=%.3f, weekend=%.3f, preHoliday=%.3f, overall=%.3f}",
                    workloadFairness, weekendFairness, preHolidayFairness, overallFairness);
        }
    }
}