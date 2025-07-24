// File: src/main/java/com/arp/evaluation/ObjectiveCalculator.java
package com.arp_1.evaluation;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.core.constraints.*;
import java.util.*;

/**
 * Calculates objective function values for solutions
 */
public class ObjectiveCalculator {
    private SoftConstraintEvaluator softConstraintEvaluator;
    private Map<ConstraintType, Double> penaltyWeights;

    public ObjectiveCalculator() {
        this.softConstraintEvaluator = new SoftConstraintEvaluator();
        initializePenaltyWeights();
    }

    private void initializePenaltyWeights() {
        penaltyWeights = new HashMap<>();
        // Default MILP-matching penalty weights
        penaltyWeights.put(ConstraintType.SC1, 10.0); // Rest days
        penaltyWeights.put(ConstraintType.SC2, 5.0); // No call requests
        penaltyWeights.put(ConstraintType.SC3, 30.0); // Shift requests (highest)
        penaltyWeights.put(ConstraintType.SC4, 8.0); // Preferred pairings
        penaltyWeights.put(ConstraintType.SC5, 10.0); // Fair distribution
        penaltyWeights.put(ConstraintType.SC6, 10.0); // Fair weekend
        penaltyWeights.put(ConstraintType.SC7, 3.0); // Fair pre-holiday
        penaltyWeights.put(ConstraintType.SC8, 8.0); // Preferences
        penaltyWeights.put(ConstraintType.SC9, 8.0); // Consecutive days
        penaltyWeights.put(ConstraintType.SC10, 8.0); // Undesired combinations
    }

    /**
     * Calculate total objective value for a solution
     */
    public double calculateObjectiveValue(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = softConstraintEvaluator.evaluateAllSoftConstraints(solution, instance);

        double totalPenalty = 0.0;
        for (ConstraintViolation violation : violations) {
            double weight = penaltyWeights.getOrDefault(violation.getConstraintType(), 1.0);
            totalPenalty += violation.getViolationCount() * weight;
        }

        return totalPenalty;
    }

    /**
     * Calculate objective value with custom weights
     */
    public double calculateObjectiveValue(Solution solution, ProblemInstance instance,
            Map<ConstraintType, Double> customWeights) {
        List<ConstraintViolation> violations = softConstraintEvaluator.evaluateAllSoftConstraints(solution, instance);

        double totalPenalty = 0.0;
        for (ConstraintViolation violation : violations) {
            double weight = customWeights.getOrDefault(violation.getConstraintType(),
                    penaltyWeights.getOrDefault(violation.getConstraintType(), 1.0));
            totalPenalty += violation.getViolationCount() * weight;
        }

        return totalPenalty;
    }

    /**
     * Get breakdown of objective components
     */
    public Map<ConstraintType, Double> getObjectiveBreakdown(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = softConstraintEvaluator.evaluateAllSoftConstraints(solution, instance);
        Map<ConstraintType, Double> breakdown = new HashMap<>();

        for (ConstraintViolation violation : violations) {
            double weight = penaltyWeights.getOrDefault(violation.getConstraintType(), 1.0);
            double penalty = violation.getViolationCount() * weight;
            breakdown.put(violation.getConstraintType(), penalty);
        }

        return breakdown;
    }

    /**
     * Set custom penalty weights
     */
    public void setPenaltyWeights(Map<ConstraintType, Double> weights) {
        this.penaltyWeights.putAll(weights);
    }

    public Map<ConstraintType, Double> getPenaltyWeights() {
        return new HashMap<>(penaltyWeights);
    }
}