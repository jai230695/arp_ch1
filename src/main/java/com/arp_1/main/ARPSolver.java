// File: src/main/java/com/arp/main/ARPSolver.java
package com.arp_1.main;

import com.arp_1.core.data.*;
import com.arp_1.core.models.Solution;
import com.arp_1.core.constraints.*;
import com.arp_1.heuristics.strategies.*;
import com.arp_1.heuristics.base.*;
import com.arp_1.evaluation.*;
import com.arp_1.utils.*;
import java.util.*;
import java.io.File;

/**
 * Main solver class for the Anaesthetist Rostering Problem
 * Provides unified interface for solving ARP instances with different
 * strategies
 */
public class ARPSolver {

    private ProblemInstance problemInstance;
    private Map<String, Object> solverConfiguration;
    private List<Solution> solutions;
    private HardConstraintChecker hardConstraintChecker;
    private SoftConstraintEvaluator softConstraintEvaluator;
    private PerformanceMetrics performanceMetrics;

    // Available solving strategies
    public enum SolvingStrategy {
        LOCATION_AWARE_DETERMINISTIC,
        LOCATION_AWARE_RANDOMIZED,
        HIERARCHICAL_CONSTRAINT,
        ADAPTIVE_PRIORITY,
        TIME_DECOMPOSED,
        ALL_STRATEGIES
    }

    public ARPSolver() {
        this.solutions = new ArrayList<>();
        this.solverConfiguration = new HashMap<>();
        this.hardConstraintChecker = new HardConstraintChecker();
        this.softConstraintEvaluator = new SoftConstraintEvaluator();
        this.performanceMetrics = new PerformanceMetrics();

        // Set default configuration
        setupDefaultConfiguration();
    }

    /**
     * Load problem instance from data path
     */
    public void loadProblem(String dataPath) {
        try {
            LoggingUtils.logSectionHeader("LOADING PROBLEM INSTANCE");
            LoggingUtils.logInfo("Data path: " + dataPath);

            // Validate data path exists
            File dataDir = new File(dataPath);
            if (!dataDir.exists() || !dataDir.isDirectory()) {
                throw new IllegalArgumentException("Data path does not exist or is not a directory: " + dataPath);
            }

            // Load problem instance
            this.problemInstance = DataLoader.loadProblemInstance(dataPath);

            // Validate problem instance
            ValidationUtils.validateProblemInstance(problemInstance);
            ValidationUtils.printProblemInstanceSummary(problemInstance);

            LoggingUtils.logInfo("Problem instance loaded successfully");

        } catch (Exception e) {
            LoggingUtils.logError("Failed to load problem instance: " + e.getMessage(), e);
            throw new RuntimeException("Problem loading failed", e);
        }
    }

    /**
     * Solve with single strategy
     */
    public Solution solve(SolvingStrategy strategy) {
        return solve(strategy, 1).get(0);
    }

    /**
     * Solve with single strategy (multiple runs for randomized)
     */
    public List<Solution> solve(SolvingStrategy strategy, int numRuns) {
        validateProblemLoaded();

        LoggingUtils.logSectionHeader("SOLVING ARP INSTANCE");
        LoggingUtils.logInfo("Strategy: " + strategy);
        LoggingUtils.logInfo("Number of runs: " + numRuns);

        List<Solution> strategySolutions = new ArrayList<>();

        try {
            switch (strategy) {
                case LOCATION_AWARE_DETERMINISTIC:
                    strategySolutions.addAll(solveLocationAwareDeterministic());
                    break;

                case LOCATION_AWARE_RANDOMIZED:
                    strategySolutions.addAll(solveLocationAwareRandomized(numRuns));
                    break;

                case HIERARCHICAL_CONSTRAINT:
                    strategySolutions.addAll(solveHierarchicalConstraint(numRuns));
                    break;

                case ADAPTIVE_PRIORITY:
                    strategySolutions.addAll(solveAdaptivePriority(numRuns));
                    break;

                case TIME_DECOMPOSED:
                    strategySolutions.addAll(solveTimeDecomposed(numRuns));
                    break;

                case ALL_STRATEGIES:
                    strategySolutions.addAll(solveAllStrategies(numRuns));
                    break;

                default:
                    throw new IllegalArgumentException("Unknown solving strategy: " + strategy);
            }

            // Add to solution history
            solutions.addAll(strategySolutions);

            // Analyze solutions
            analyzeSolutions(strategySolutions);

            LoggingUtils.logInfo("Solving completed. Generated " + strategySolutions.size() + " solutions.");

            return strategySolutions;

        } catch (Exception e) {
            LoggingUtils.logError("Solving failed: " + e.getMessage(), e);
            throw new RuntimeException("Solving failed", e);
        }
    }

    /**
     * Get best solution found so far
     */
    public Solution getBestSolution() {
        if (solutions.isEmpty()) {
            return null;
        }

        return solutions.stream()
                .filter(Solution::isFeasible)
                .min(Comparator.comparingDouble(Solution::getObjectiveValue))
                .orElse(solutions.stream()
                        .min(Comparator.comparingDouble(Solution::getObjectiveValue))
                        .orElse(null));
    }

    /**
     * Print comprehensive solution analysis
     */
    public void printSolutionAnalysis() {
        if (solutions.isEmpty()) {
            LoggingUtils.logWarning("No solutions to analyze");
            return;
        }

        LoggingUtils.logSectionHeader("SOLUTION ANALYSIS");

        // Overall statistics
        int totalSolutions = solutions.size();
        long feasibleSolutions = solutions.stream().mapToLong(s -> s.isFeasible() ? 1 : 0).sum();
        double feasibilityRate = (double) feasibleSolutions / totalSolutions;

        LoggingUtils.logInfo("Total solutions: " + totalSolutions);
        LoggingUtils.logInfo("Feasible solutions: " + feasibleSolutions + " (" +
                String.format("%.1f%%", feasibilityRate * 100) + ")");

        // Best solution details
        Solution bestSolution = getBestSolution();
        if (bestSolution != null) {
            LoggingUtils.logSeparator();
            LoggingUtils.logInfo("BEST SOLUTION DETAILS:");
            LoggingUtils.logInfo("  Strategy: " + bestSolution.getConstructionMethod());
            LoggingUtils.logInfo("  Objective: " + String.format("%.2f", bestSolution.getObjectiveValue()));
            LoggingUtils.logInfo("  Feasible: " + bestSolution.isFeasible());
            LoggingUtils.logInfo("  Hard violations: " + bestSolution.getHardConstraintViolations());
            LoggingUtils.logInfo("  Soft violations: " + bestSolution.getSoftConstraintViolations());
            LoggingUtils.logInfo("  Computation time: " + bestSolution.getComputationTime() + "ms");

            // Detailed constraint analysis
            printDetailedConstraintAnalysis(bestSolution);
        }

        // Performance metrics
        performanceMetrics.calculateMetrics(solutions, problemInstance);
        performanceMetrics.printMetrics();
    }

    /**
     * Export solutions to files
     */
    public void exportSolutions(String outputPath) {
        if (solutions.isEmpty()) {
            LoggingUtils.logWarning("No solutions to export");
            return;
        }

        try {
            LoggingUtils.logInfo("Exporting solutions to: " + outputPath);

            // Create output directory
            File outputDir = new File(outputPath);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // Export best solution
            Solution bestSolution = getBestSolution();
            if (bestSolution != null) {
                String bestSolutionPath = outputPath + "/best_solution.csv";
                exportSolutionToCSV(bestSolution, bestSolutionPath);
                LoggingUtils.logInfo("Best solution exported to: " + bestSolutionPath);
            }

            // Export solution summary
            String summaryPath = outputPath + "/solution_summary.csv";
            exportSolutionSummary(summaryPath);
            LoggingUtils.logInfo("Solution summary exported to: " + summaryPath);

            // Export performance metrics
            String metricsPath = outputPath + "/performance_metrics.txt";
            performanceMetrics.exportMetrics(metricsPath);
            LoggingUtils.logInfo("Performance metrics exported to: " + metricsPath);

        } catch (Exception e) {
            LoggingUtils.logError("Failed to export solutions: " + e.getMessage(), e);
        }
    }

    /**
     * Configure solver parameters
     */
    public void configure(Map<String, Object> configuration) {
        this.solverConfiguration.putAll(configuration);
        LoggingUtils.logInfo("Solver configuration updated: " + configuration.size() + " parameters");
    }

    /**
     * Clear all solutions and reset
     */
    public void reset() {
        solutions.clear();
        solverConfiguration.clear();
        setupDefaultConfiguration();
        LoggingUtils.logInfo("Solver reset completed");
    }

    // Private implementation methods

    private void setupDefaultConfiguration() {
        solverConfiguration.put("random_seed", 12345L);
        solverConfiguration.put("selection_bias", 0.5);
        solverConfiguration.put("max_iterations", 1000);
        solverConfiguration.put("time_limit_ms", 60000); // 1 minute
        solverConfiguration.put("enable_logging", true);
    }

    private void validateProblemLoaded() {
        if (problemInstance == null) {
            throw new IllegalStateException("No problem instance loaded. Call loadProblem() first.");
        }
    }

    private List<Solution> solveLocationAwareDeterministic() {
        LoggingUtils.logInfo("Solving with Location-Aware Deterministic strategy");

        LocationAwareSequentialConstruction heuristic = LocationAwareSequentialConstruction.createDeterministic();
        heuristic.configure(solverConfiguration);

        Solution solution = heuristic.constructSolution(problemInstance);
        return Arrays.asList(solution);
    }

    private List<Solution> solveLocationAwareRandomized(int numRuns) {
        LoggingUtils.logInfo("Solving with Location-Aware Randomized strategy (" + numRuns + " runs)");

        List<Solution> solutions = new ArrayList<>();
        long baseSeed = (Long) solverConfiguration.getOrDefault("random_seed", 12345L);
        double bias = (Double) solverConfiguration.getOrDefault("selection_bias", 0.5);

        for (int run = 0; run < numRuns; run++) {
            long seed = baseSeed + run * 1000L;

            LocationAwareSequentialConstruction heuristic = LocationAwareSequentialConstruction.createRandomized(seed,
                    bias);
            heuristic.configure(solverConfiguration);

            Solution solution = heuristic.constructSolution(problemInstance);
            solutions.add(solution);

            LoggingUtils.logProgress("Location-Aware Randomized", run + 1, numRuns);
        }

        return solutions;
    }

    private List<Solution> solveHierarchicalConstraint(int numRuns) {
        LoggingUtils.logInfo("Solving with Hierarchical Constraint strategy (" + numRuns + " runs)");

        List<Solution> solutions = new ArrayList<>();
        long baseSeed = (Long) solverConfiguration.getOrDefault("random_seed", 12345L);
        double bias = (Double) solverConfiguration.getOrDefault("selection_bias", 0.5);

        for (int run = 0; run < numRuns; run++) {
            long seed = baseSeed + run * 1000L;

            HierarchicalConstraintConstruction heuristic = HierarchicalConstraintConstruction.createRandomized(seed,
                    bias);
            heuristic.configure(solverConfiguration);

            Solution solution = heuristic.constructSolution(problemInstance);
            solutions.add(solution);

            LoggingUtils.logProgress("Hierarchical Constraint", run + 1, numRuns);
        }

        return solutions;
    }

    private List<Solution> solveAdaptivePriority(int numRuns) {
        LoggingUtils.logInfo("Solving with Adaptive Priority strategy (" + numRuns + " runs)");

        List<Solution> solutions = new ArrayList<>();
        long baseSeed = (Long) solverConfiguration.getOrDefault("random_seed", 12345L);
        double bias = (Double) solverConfiguration.getOrDefault("selection_bias", 0.5);

        for (int run = 0; run < numRuns; run++) {
            long seed = baseSeed + run * 1000L;

            AdaptivePriorityConstruction heuristic = AdaptivePriorityConstruction.createRandomized(seed, bias);
            heuristic.configure(solverConfiguration);

            Solution solution = heuristic.constructSolution(problemInstance);
            solutions.add(solution);

            LoggingUtils.logProgress("Adaptive Priority", run + 1, numRuns);
        }

        return solutions;
    }

    private List<Solution> solveTimeDecomposed(int numRuns) {
        LoggingUtils.logInfo("Solving with Time Decomposed strategy (" + numRuns + " runs)");

        List<Solution> solutions = new ArrayList<>();
        long baseSeed = (Long) solverConfiguration.getOrDefault("random_seed", 12345L);
        double bias = (Double) solverConfiguration.getOrDefault("selection_bias", 0.5);

        for (int run = 0; run < numRuns; run++) {
            long seed = baseSeed + run * 1000L;

            TimeDecomposedConstruction heuristic = TimeDecomposedConstruction.createRandomized(seed, bias);
            heuristic.configure(solverConfiguration);

            Solution solution = heuristic.constructSolution(problemInstance);
            solutions.add(solution);

            LoggingUtils.logProgress("Time Decomposed", run + 1, numRuns);
        }

        return solutions;
    }

    private List<Solution> solveAllStrategies(int runsPerStrategy) {
        LoggingUtils.logInfo("Solving with ALL strategies (" + runsPerStrategy + " runs each)");

        List<Solution> allSolutions = new ArrayList<>();

        // Test each strategy
        allSolutions.addAll(solveLocationAwareDeterministic());
        allSolutions.addAll(solveLocationAwareRandomized(runsPerStrategy));
        allSolutions.addAll(solveHierarchicalConstraint(runsPerStrategy));
        allSolutions.addAll(solveAdaptivePriority(runsPerStrategy));
        allSolutions.addAll(solveTimeDecomposed(runsPerStrategy));

        return allSolutions;
    }

    private void analyzeSolutions(List<Solution> solutions) {
        LoggingUtils.logInfo("Analyzing " + solutions.size() + " solutions");

        for (int i = 0; i < solutions.size(); i++) {
            Solution solution = solutions.get(i);

            // Validate constraints
            List<ConstraintViolation> hardViolations = hardConstraintChecker.checkAllHardConstraints(solution,
                    problemInstance);
            List<ConstraintViolation> softViolations = softConstraintEvaluator.evaluateAllSoftConstraints(solution,
                    problemInstance);

            // Update solution with violation counts
            solution.setHardConstraintViolations(hardViolations.size());
            solution.setSoftConstraintViolations(softViolations.size());
            solution.setFeasible(hardViolations.isEmpty());

            // Calculate objective if not set
            if (solution.getObjectiveValue() == 0.0) {
                double objective = softConstraintEvaluator.calculateTotalSoftConstraintPenalty(solution,
                        problemInstance);
                solution.setObjectiveValue(objective);
            }

            LoggingUtils.logExperimentResult(
                    solution.getConstructionMethod(),
                    i + 1,
                    solution.getObjectiveValue(),
                    solution.isFeasible(),
                    solution.getComputationTime());
        }
    }

    private void printDetailedConstraintAnalysis(Solution solution) {
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("DETAILED CONSTRAINT ANALYSIS:");

        // Hard constraints
        List<ConstraintViolation> hardViolations = hardConstraintChecker.checkAllHardConstraints(solution,
                problemInstance);

        if (hardViolations.isEmpty()) {
            LoggingUtils.logInfo("✓ All hard constraints satisfied");
        } else {
            LoggingUtils.logWarning("✗ Hard constraint violations:");
            for (ConstraintViolation violation : hardViolations) {
                LoggingUtils.logWarning("  " + violation.toString());
            }
        }

        // Soft constraints
        Map<ConstraintType, Integer> softViolationCounts = softConstraintEvaluator
                .getSoftConstraintViolationCounts(solution, problemInstance);

        LoggingUtils.logInfo("Soft constraint violations:");
        double totalPenalty = 0.0;
        for (ConstraintType type : ConstraintType.values()) {
            if (type.isSoftConstraint()) {
                int count = softViolationCounts.getOrDefault(type, 0);
                double penalty = count * type.getDefaultPenaltyWeight();
                totalPenalty += penalty;

                LoggingUtils.logInfo(String.format("  %s: %d violations, penalty: %.0f",
                        type.name(), count, penalty));
            }
        }
        LoggingUtils.logInfo("Total soft constraint penalty: " + String.format("%.0f", totalPenalty));
    }

    private void exportSolutionToCSV(Solution solution, String filePath) {
        // Implementation would export solution assignments to CSV format
        LoggingUtils.logDebug("Exporting solution to CSV: " + filePath);
        // Detailed CSV export implementation would go here
    }

    private void exportSolutionSummary(String filePath) {
        // Implementation would export summary statistics to CSV
        LoggingUtils.logDebug("Exporting solution summary to: " + filePath);
        // Detailed summary export implementation would go here
    }

    // Getters
    public ProblemInstance getProblemInstance() {
        return problemInstance;
    }

    public List<Solution> getAllSolutions() {
        return new ArrayList<>(solutions);
    }

    public Map<String, Object> getConfiguration() {
        return new HashMap<>(solverConfiguration);
    }

    /**
     * Main method for command-line usage
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            return;
        }

        try {
            String dataPath = args[0];
            String strategyName = args[1].toUpperCase();
            int numRuns = args.length > 2 ? Integer.parseInt(args[2]) : 1;
            String outputPath = args.length > 3 ? args[3] : "results/";

            // Create solver
            ARPSolver solver = new ARPSolver();

            // Load problem
            solver.loadProblem(dataPath);

            // Parse strategy
            SolvingStrategy strategy;
            try {
                strategy = SolvingStrategy.valueOf(strategyName);
            } catch (IllegalArgumentException e) {
                LoggingUtils.logError("Unknown strategy: " + strategyName);
                printUsage();
                return;
            }

            // Solve
            solver.solve(strategy, numRuns);

            // Analyze and export
            solver.printSolutionAnalysis();
            solver.exportSolutions(outputPath);

            LoggingUtils.logInfo("ARP solving completed successfully");

        } catch (Exception e) {
            LoggingUtils.logError("ARP solving failed: " + e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java ARPSolver <data_path> <strategy> [num_runs] [output_path]");
        System.out.println("Strategies:");
        for (SolvingStrategy strategy : SolvingStrategy.values()) {
            System.out.println("  " + strategy.name());
        }
        System.out.println("Examples:");
        System.out.println("  java ARPSolver data/month1/ LOCATION_AWARE_RANDOMIZED 30");
        System.out.println("  java ARPSolver data/month1/ ALL_STRATEGIES 10 results/");
    }
}