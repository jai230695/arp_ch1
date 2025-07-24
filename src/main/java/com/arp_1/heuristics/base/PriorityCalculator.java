// File: src/main/java/com/arp/heuristics/base/PriorityCalculator.java
package com.arp_1.heuristics.base;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;

/**
 * Calculates priority scores for anaesthetist assignments
 */
public class PriorityCalculator {

    // Weight factors for priority calculation
    private static final double QUALIFICATION_WEIGHT = 100.0;
    private static final double PREFERENCE_WEIGHT = 50.0;
    private static final double WORKLOAD_BALANCE_WEIGHT = 30.0;
    private static final double FAIRNESS_WEIGHT = 25.0;
    private static final double REST_COMPLIANCE_WEIGHT = 20.0;
    private static final double REQUEST_FULFILLMENT_WEIGHT = 15.0;

    public double calculatePriority(String anaesthetistId, Workstation workstation,
            PlanningDay day, ProblemInstance instance,
            Solution currentSolution) {

        Anaesthetist anaesthetist = instance.getAnaesthetistById(anaesthetistId);
        if (anaesthetist == null)
            return 0.0;

        double priority = 0.0;

        // 1. Qualification and preference matching (highest priority)
        priority += calculateQualificationScore(anaesthetist, workstation);

        // 2. Workload balance consideration
        priority += calculateWorkloadBalanceScore(anaesthetist, workstation, instance, currentSolution);

        // 3. Fairness consideration (historical assignments)
        priority += calculateFairnessScore(anaesthetist, workstation, instance, currentSolution);

        // 4. Rest compliance bonus
        priority += calculateRestComplianceScore(anaesthetist, day, instance, currentSolution);

        // 5. Request fulfillment bonus
        priority += calculateRequestFulfillmentScore(anaesthetist, day, workstation, instance);

        // 6. Special location-specific bonuses
        priority += calculateSpecialLocationScore(anaesthetist, workstation, day, instance, currentSolution);

        return priority;
    }

    private double calculateQualificationScore(Anaesthetist anaesthetist, Workstation workstation) {
        if (anaesthetist.hasPreferenceFor(workstation.getId())) {
            return QUALIFICATION_WEIGHT + PREFERENCE_WEIGHT;
        } else if (anaesthetist.isQualifiedFor(workstation.getId())) {
            return QUALIFICATION_WEIGHT;
        } else {
            return 0.0; // Should not happen in eligible candidates
        }
    }

    private double calculateWorkloadBalanceScore(Anaesthetist anaesthetist, Workstation workstation,
            ProblemInstance instance, Solution currentSolution) {
        // Calculate current workload for this anaesthetist
        int currentAssignments = currentSolution.countMonthlyAssignments(anaesthetist.getId(), workstation.getId());

        // Calculate average assignments for all qualified anaesthetists
        double totalAssignments = 0.0;
        int qualifiedCount = 0;

        for (Anaesthetist other : instance.getQualifiedAnaesthetists(workstation.getId())) {
            totalAssignments += currentSolution.countMonthlyAssignments(other.getId(), workstation.getId());
            qualifiedCount++;
        }

        double averageAssignments = qualifiedCount > 0 ? totalAssignments / qualifiedCount : 0.0;

        // Bonus for anaesthetists with fewer assignments (promotes balance)
        double deviation = currentAssignments - averageAssignments;
        return WORKLOAD_BALANCE_WEIGHT * Math.max(0, -deviation);
    }

    private double calculateFairnessScore(Anaesthetist anaesthetist, Workstation workstation,
            ProblemInstance instance, Solution currentSolution) {
        // Include historical assignments for long-term fairness
        int currentAssignments = currentSolution.countMonthlyAssignments(anaesthetist.getId(), workstation.getId());
        int historicalAssignments = instance.getHistoricalAssignments(anaesthetist.getId(), workstation.getId());
        int totalAssignments = currentAssignments + historicalAssignments;

        // Calculate average total assignments for qualified anaesthetists
        double totalAllAssignments = 0.0;
        int qualifiedCount = 0;

        for (Anaesthetist other : instance.getQualifiedAnaesthetists(workstation.getId())) {
            int otherCurrent = currentSolution.countMonthlyAssignments(other.getId(), workstation.getId());
            int otherHistorical = instance.getHistoricalAssignments(other.getId(), workstation.getId());
            totalAllAssignments += otherCurrent + otherHistorical;
            qualifiedCount++;
        }

        double averageTotalAssignments = qualifiedCount > 0 ? totalAllAssignments / qualifiedCount : 0.0;

        // Bonus for anaesthetists with fewer total assignments
        double fairnessDeviation = totalAssignments - averageTotalAssignments;
        return FAIRNESS_WEIGHT * Math.max(0, -fairnessDeviation);
    }

    private double calculateRestComplianceScore(Anaesthetist anaesthetist, PlanningDay day,
            ProblemInstance instance, Solution currentSolution) {
        double bonus = 0.0;

        // Bonus for adequate rest after previous assignments
        if (day.getDayNumber() > 1) {
            boolean previousSGOT = currentSolution.isAssignedMonthly(anaesthetist.getId(), "SGOT",
                    day.getDayNumber() - 1);
            if (!previousSGOT) {
                bonus += REST_COMPLIANCE_WEIGHT * 0.5; // No recent SGOT assignment
            }
        }

        // Bonus for not having conflicting assignments in 3-day window
        int sgotCount = 0;
        for (int checkDay = Math.max(1, day.getDayNumber() - 1); checkDay <= Math.min(28,
                day.getDayNumber() + 1); checkDay++) {
            if (currentSolution.isAssignedMonthlyAnyLocation(anaesthetist.getId(), checkDay)) {
                sgotCount++;
            }
        }

        if (sgotCount < 2) {
            bonus += REST_COMPLIANCE_WEIGHT * 0.5; // Good rest pattern
        }

        return bonus;
    }

    private double calculateRequestFulfillmentScore(Anaesthetist anaesthetist, PlanningDay day,
            Workstation workstation, ProblemInstance instance) {
        double bonus = 0.0;

        // Check if assignment fulfills anaesthetist requests
        if (instance.hasMorningShiftRequest(anaesthetist.getId(), day.getDayNumber()) &&
                workstation.isMorningShift()) {
            bonus += REQUEST_FULFILLMENT_WEIGHT;
        }

        if (instance.hasEveningShiftRequest(anaesthetist.getId(), day.getDayNumber()) &&
                workstation.isEveningShift()) {
            bonus += REQUEST_FULFILLMENT_WEIGHT;
        }

        // Penalty for assigning to on-call when no-call requested
        if (instance.hasNoCallRequest(anaesthetist.getId(), day.getDayNumber()) &&
                workstation.isOnCallShift()) {
            bonus -= REQUEST_FULFILLMENT_WEIGHT;
        }

        return bonus;
    }

    private double calculateSpecialLocationScore(Anaesthetist anaesthetist, Workstation workstation,
            PlanningDay day, ProblemInstance instance,
            Solution currentSolution) {
        double bonus = 0.0;

        // SGOT special handling
        if (workstation.isSGOT()) {
            // Bonus for junior anaesthetists (typically more available for SGOT)
            if (anaesthetist.isJunior()) {
                bonus += 10.0;
            }

            // Penalty if assigned to SGOT recently
            for (int checkDay = Math.max(1, day.getDayNumber() - 3); checkDay < day.getDayNumber(); checkDay++) {
                if (currentSolution.isAssignedMonthly(anaesthetist.getId(), "SGOT", checkDay)) {
                    bonus -= 20.0; // Avoid frequent SGOT assignments
                    break;
                }
            }
        }

        // CICU special handling (prefers consecutive assignments)
        if (workstation.isCICU()) {
            // Bonus if can create consecutive CICU assignments
            boolean previousCICU = day.getDayNumber() > 1 &&
                    currentSolution.isAssignedMonthly(anaesthetist.getId(), "CICU", day.getDayNumber() - 1);
            boolean nextCICUPossible = day.getDayNumber() < 28; // Can check next day later

            if (previousCICU || nextCICUPossible) {
                bonus += 15.0; // Encourage consecutive CICU assignments
            }
        }

        // Weekend pairing bonus
        if (day.isWeekendOrHoliday()) {
            // Check if this assignment would complete a weekend pair
            for (WeekendPair pair : instance.getWeekendPairs()) {
                if (pair.containsDay(day.getDayNumber())) {
                    int otherDay = pair.getOtherDay(day.getDayNumber());
                    if (currentSolution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), otherDay)) {
                        bonus += 25.0; // Strong bonus for completing weekend pairs
                    }
                }
            }
        }

        return bonus;
    }

    // Method for calculating priority with custom weights
    public double calculatePriorityWithWeights(String anaesthetistId, Workstation workstation,
            PlanningDay day, ProblemInstance instance,
            Solution currentSolution,
            java.util.Map<String, Double> customWeights) {

        // Use custom weights if provided, otherwise use defaults
        double qualWeight = customWeights.getOrDefault("qualification", QUALIFICATION_WEIGHT);
        double prefWeight = customWeights.getOrDefault("preference", PREFERENCE_WEIGHT);
        double workloadWeight = customWeights.getOrDefault("workload", WORKLOAD_BALANCE_WEIGHT);
        double fairnessWeight = customWeights.getOrDefault("fairness", FAIRNESS_WEIGHT);
        double restWeight = customWeights.getOrDefault("rest", REST_COMPLIANCE_WEIGHT);
        double requestWeight = customWeights.getOrDefault("request", REQUEST_FULFILLMENT_WEIGHT);

        Anaesthetist anaesthetist = instance.getAnaesthetistById(anaesthetistId);
        if (anaesthetist == null)
            return 0.0;

        double priority = 0.0;

        // Apply custom weights to each component
        if (anaesthetist.hasPreferenceFor(workstation.getId())) {
            priority += qualWeight + prefWeight;
        } else if (anaesthetist.isQualifiedFor(workstation.getId())) {
            priority += qualWeight;
        }

        // Add other components with custom weights...
        // (Implementation would use the custom weights instead of constants)

        return priority;
    }
}