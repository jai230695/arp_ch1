// File: src/main/java/com/arp/heuristics/strategies/AdaptivePriorityConstruction.java
package com.arp_1.heuristics.strategies;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.core.constraints.*;
import com.arp_1.heuristics.base.*;
import com.arp_1.heuristics.integration.TemporalCoordinator;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Adaptive priority construction strategy
 * Dynamically adjusts priorities based on solution progress
 */
public class AdaptivePriorityConstruction implements ConstructiveHeuristic {
    private TemporalCoordinator coordinator;
    private GreedySelector selector;
    private String heuristicName;
    private Map<String, Object> configuration;
    private AdaptivePriorityManager priorityManager;
    private SoftConstraintEvaluator softConstraintEvaluator;

    public AdaptivePriorityConstruction(GreedySelector selector) {
        this.selector = selector;
        this.coordinator = new TemporalCoordinator(selector);
        this.heuristicName = "ADAPTIVE_PRIORITY_" + selector.getSelectorType();
        this.configuration = new HashMap<>();
        this.priorityManager = new AdaptivePriorityManager();
        this.softConstraintEvaluator = new SoftConstraintEvaluator();
    }

    @Override
    public Solution constructSolution(ProblemInstance instance) {
        long startTime = System.currentTimeMillis();

        LoggingUtils.logSectionHeader("ADAPTIVE PRIORITY CONSTRUCTION");
        LoggingUtils.logInfo("Strategy: " + heuristicName);
        LoggingUtils.logInfo("Adaptive priority management activated");

        try {
            // Initialize adaptive priority system
            priorityManager.initialize(instance);

            // Configure selector with adaptive parameters
            Map<String, Object> adaptiveConfig = new HashMap<>(configuration);
            adaptiveConfig.put("adaptive_priorities", true);
            adaptiveConfig.put("priority_manager", priorityManager);

            selector.configure(adaptiveConfig);

            // Build solution with adaptive priority adjustments
            Solution solution = coordinator.buildIntegratedSolution(instance);

            // Apply final adaptive optimization
            solution = priorityManager.finalizeAdaptiveOptimization(solution, instance, softConstraintEvaluator);

            solution.setConstructionMethod(heuristicName);
            solution.setCreationTime(startTime);

            long endTime = System.currentTimeMillis();
            solution.setComputationTime(endTime - startTime);

            LoggingUtils.logSolutionStats("ADAPTIVE_COMPLETE",
                    solution.getObjectiveValue(),
                    solution.getHardConstraintViolations(),
                    solution.getSoftConstraintViolations(),
                    endTime - startTime);

            return solution;

        } catch (Exception e) {
            LoggingUtils.logError("Adaptive construction failed: " + e.getMessage(), e);
            throw new RuntimeException("Failed to construct adaptive solution", e);
        }
    }

    @Override
    public String getHeuristicName() {
        return heuristicName;
    }

    @Override
    public void configure(Map<String, Object> parameters) {
        this.configuration.putAll(parameters);
        if (selector != null) {
            selector.configure(parameters);
        }
    }

    @Override
    public void reset() {
        this.configuration.clear();
        this.priorityManager.reset();
        if (selector != null) {
            selector.configure(new HashMap<>());
        }
    }

    public static AdaptivePriorityConstruction createDeterministic() {
        return new AdaptivePriorityConstruction(new DeterministicSelector());
    }

    public static AdaptivePriorityConstruction createRandomized(long seed, double bias) {
        return new AdaptivePriorityConstruction(new RandomizedSelector(seed, bias));
    }

    public void setSelector(GreedySelector newSelector) {
        this.selector = newSelector;
        this.coordinator.setGreedySelector(newSelector);
        this.heuristicName = "ADAPTIVE_PRIORITY_" + newSelector.getSelectorType();
    }

    public GreedySelector getSelector() {
        return selector;
    }

    @Override
    public String toString() {
        return String.format("AdaptivePriorityConstruction{name='%s', selector=%s}",
                heuristicName, selector);
    }

    // Inner class for managing adaptive priorities
    private static class AdaptivePriorityManager {
        private Map<String, Double> currentPriorityWeights;
        private Map<String, Integer> constraintViolationHistory;
        private Map<String, Double> adaptationRates;
        private int constructionPhase;
        private static final double MIN_WEIGHT = 0.1;
        private static final double MAX_WEIGHT = 500.0;

        public void initialize(ProblemInstance instance) {
            this.currentPriorityWeights = new HashMap<>();
            this.constraintViolationHistory = new HashMap<>();
            this.adaptationRates = new HashMap<>();
            this.constructionPhase = 0;

            // Initialize default weights
            initializeDefaultWeights();

            // Initialize adaptation rates
            initializeAdaptationRates();

            LoggingUtils.logInfo("Adaptive priority manager initialized with " +
                    currentPriorityWeights.size() + " priority weights");
        }

        private void initializeDefaultWeights() {
            currentPriorityWeights.put("qualification", 100.0);
            currentPriorityWeights.put("preference", 50.0);
            currentPriorityWeights.put("workload", 30.0);
            currentPriorityWeights.put("fairness", 25.0);
            currentPriorityWeights.put("rest", 20.0);
            currentPriorityWeights.put("request", 15.0);
            currentPriorityWeights.put("sgot_specific", 40.0);
            currentPriorityWeights.put("weekend_pairing", 35.0);
            currentPriorityWeights.put("cicu_consecutive", 30.0);
        }

        private void initializeAdaptationRates() {
            adaptationRates.put("qualification", 0.05); // Slow adaptation - core requirement
            adaptationRates.put("preference", 0.15); // Medium adaptation
            adaptationRates.put("workload", 0.20); // Fast adaptation - balance critical
            adaptationRates.put("fairness", 0.10); // Slow adaptation - long-term goal
            adaptationRates.put("rest", 0.25); // Fast adaptation - safety critical
            adaptationRates.put("request", 0.20); // Fast adaptation - satisfaction important
            adaptationRates.put("sgot_specific", 0.15); // Medium adaptation
            adaptationRates.put("weekend_pairing", 0.10); // Slow adaptation
            adaptationRates.put("cicu_consecutive", 0.15); // Medium adaptation
        }

        public void updatePriorities(Solution currentSolution, ProblemInstance instance,
                SoftConstraintEvaluator evaluator) {
            constructionPhase++;

            // Analyze current solution quality
            analyzeCurrentSolution(currentSolution, instance, evaluator);

            // Adjust weights based on analysis
            adjustPriorityWeights();

            LoggingUtils.logDebug("Priority weights updated for phase " + constructionPhase);

            // Log current weights periodically
            if (constructionPhase % 5 == 0) {
                logCurrentWeights();
            }
        }

        private void analyzeCurrentSolution(Solution solution, ProblemInstance instance,
                SoftConstraintEvaluator evaluator) {

            // Get constraint violations
            Map<ConstraintType, Integer> currentViolations = evaluator.getSoftConstraintViolationCounts(solution,
                    instance);

            // Update violation history
            for (Map.Entry<ConstraintType, Integer> entry : currentViolations.entrySet()) {
                String constraintKey = mapConstraintToWeight(entry.getKey());
                if (constraintKey != null) {
                    constraintViolationHistory.merge(constraintKey, entry.getValue(), Integer::sum);
                }
            }

            // Analyze workload balance
            analyzeWorkloadBalance(solution, instance);

            // Analyze fairness distribution
            analyzeFairnessDistribution(solution, instance);
        }

        private String mapConstraintToWeight(ConstraintType constraintType) {
            switch (constraintType) {
                case SC1:
                    return "rest";
                case SC2:
                    return "request";
                case SC3:
                    return "request";
                case SC4:
                    return "weekend_pairing";
                case SC5:
                    return "fairness";
                case SC6:
                    return "fairness";
                case SC7:
                    return "fairness";
                case SC8:
                    return "preference";
                case SC9:
                    return "cicu_consecutive";
                case SC10:
                    return "preference";
                default:
                    return null;
            }
        }

        private void analyzeWorkloadBalance(Solution solution, ProblemInstance instance) {
            // Analyze workload distribution and identify imbalances
            // This would check if certain anaesthetists are being overloaded

            // For now, simplified analysis
            int workloadViolations = solution.getConstraintViolations()
                    .getOrDefault("SC5", 0); // Fair workload distribution

            if (workloadViolations > 3) {
                constraintViolationHistory.merge("workload", workloadViolations, Integer::sum);
            }
        }

        private void analyzeFairnessDistribution(Solution solution, ProblemInstance instance) {
            // Analyze fairness metrics
            int fairnessViolations = solution.getConstraintViolations()
                    .getOrDefault("SC5", 0) +
                    solution.getConstraintViolations().getOrDefault("SC6", 0) +
                    solution.getConstraintViolations().getOrDefault("SC7", 0);

            if (fairnessViolations > 2) {
                constraintViolationHistory.merge("fairness", fairnessViolations, Integer::sum);
            }
        }

        private void adjustPriorityWeights() {
            for (Map.Entry<String, Integer> entry : constraintViolationHistory.entrySet()) {
                String weightKey = entry.getKey();
                int totalViolations = entry.getValue();

                if (totalViolations > getViolationThreshold(weightKey)) {
                    adaptWeightUpward(weightKey, totalViolations);
                } else if (totalViolations == 0 && constructionPhase > 3) {
                    adaptWeightDownward(weightKey);
                }
            }

            // Normalize weights to prevent extreme values
            normalizeWeights();
        }

        private int getViolationThreshold(String weightKey) {
            switch (weightKey) {
                case "rest":
                    return 1; // Very low tolerance for rest violations
                case "workload":
                    return 2; // Low tolerance for workload imbalance
                case "request":
                    return 3; // Medium tolerance for request violations
                case "fairness":
                    return 5; // Higher tolerance for fairness (long-term)
                default:
                    return 3;
            }
        }

        private void adaptWeightUpward(String weightKey, int violations) {
            double currentWeight = currentPriorityWeights.get(weightKey);
            double adaptationRate = adaptationRates.get(weightKey);

            // Increase weight based on violation severity
            double violationFactor = Math.min(2.0, 1.0 + (violations / 10.0));
            double newWeight = currentWeight * (1.0 + adaptationRate * violationFactor);

            // Clamp to maximum
            newWeight = Math.min(newWeight, MAX_WEIGHT);

            currentPriorityWeights.put(weightKey, newWeight);

            LoggingUtils.logDebug("Adapted weight upward: " + weightKey +
                    " " + String.format("%.1f", currentWeight) + " -> " + String.format("%.1f", newWeight) +
                    " (violations: " + violations + ")");
        }

        private void adaptWeightDownward(String weightKey) {
            double currentWeight = currentPriorityWeights.get(weightKey);
            double adaptationRate = adaptationRates.get(weightKey);

            // Decrease weight slowly when no violations
            double newWeight = currentWeight * (1.0 - adaptationRate * 0.5);

            // Clamp to minimum
            newWeight = Math.max(newWeight, MIN_WEIGHT);

            currentPriorityWeights.put(weightKey, newWeight);

            LoggingUtils.logDebug("Adapted weight downward: " + weightKey +
                    " " + String.format("%.1f", currentWeight) + " -> " + String.format("%.1f", newWeight));
        }

        private void normalizeWeights() {
            // Ensure weights remain within reasonable bounds relative to each other
            double totalWeight = currentPriorityWeights.values().stream()
                    .mapToDouble(Double::doubleValue).sum();

            if (totalWeight > 1000.0) {
                // Scale down all weights proportionally
                double scaleFactor = 800.0 / totalWeight;
                currentPriorityWeights.replaceAll((key, value) -> value * scaleFactor);

                LoggingUtils.logDebug("Normalized weights by factor: " + String.format("%.3f", scaleFactor));
            }
        }

        private void logCurrentWeights() {
            LoggingUtils.logDebug("Current adaptive weights (phase " + constructionPhase + "):");
            for (Map.Entry<String, Double> entry : currentPriorityWeights.entrySet()) {
                LoggingUtils.logDebug("  " + entry.getKey() + ": " + String.format("%.1f", entry.getValue()));
            }
        }

        public Map<String, Double> getCurrentWeights() {
            return new HashMap<>(currentPriorityWeights);
        }

        public Solution finalizeAdaptiveOptimization(Solution solution, ProblemInstance instance,
                SoftConstraintEvaluator evaluator) {
            LoggingUtils.logInfo("Finalizing adaptive optimization");

            // Final weight adjustment based on complete solution
            updatePriorities(solution, instance, evaluator);

            // Recalculate objective with adapted weights
            double adaptedObjective = calculateAdaptedObjective(solution, instance, evaluator);
            solution.setObjectiveValue(adaptedObjective);

            // Log final adaptation summary
            logAdaptationSummary();

            return solution;
        }

        private double calculateAdaptedObjective(Solution solution, ProblemInstance instance,
                SoftConstraintEvaluator evaluator) {

            Map<ConstraintType, Integer> violationCounts = evaluator.getSoftConstraintViolationCounts(solution,
                    instance);

            double totalPenalty = 0.0;

            for (Map.Entry<ConstraintType, Integer> entry : violationCounts.entrySet()) {
                ConstraintType type = entry.getKey();
                int count = entry.getValue();

                String weightKey = mapConstraintToWeight(type);
                if (weightKey != null) {
                    double adaptedWeight = currentPriorityWeights.get(weightKey);
                    totalPenalty += adaptedWeight * count;
                } else {
                    // Use default weight for unmapped constraints
                    totalPenalty += type.getDefaultPenaltyWeight() * count;
                }
            }

            return totalPenalty;
        }

        private void logAdaptationSummary() {
            LoggingUtils.logInfo("Adaptive optimization summary:");
            LoggingUtils.logInfo("  Construction phases: " + constructionPhase);
            LoggingUtils.logInfo("  Total constraint types monitored: " + constraintViolationHistory.size());

            // Log most adapted weights
            String mostIncreased = currentPriorityWeights.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("none");

            LoggingUtils.logInfo("  Most adapted weight: " + mostIncreased +
                    " (" + String.format("%.1f", currentPriorityWeights.get(mostIncreased)) + ")");
        }

        public void reset() {
            if (currentPriorityWeights != null) {
                currentPriorityWeights.clear();
            }
            if (constraintViolationHistory != null) {
                constraintViolationHistory.clear();
            }
            if (adaptationRates != null) {
                adaptationRates.clear();
            }
            this.constructionPhase = 0;
        }
    }
}