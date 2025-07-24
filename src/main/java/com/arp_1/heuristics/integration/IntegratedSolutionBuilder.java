// File: src/main/java/com/arp/heuristics/integration/IntegratedSolutionBuilder.java
package com.arp_1.heuristics.integration;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.core.constraints.*;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Builds and manages integrated solutions combining monthly and weekly rosters
 */
public class IntegratedSolutionBuilder {
    private HardConstraintChecker hardConstraintChecker;
    private SoftConstraintEvaluator softConstraintEvaluator;

    public IntegratedSolutionBuilder() {
        this.hardConstraintChecker = new HardConstraintChecker();
        this.softConstraintEvaluator = new SoftConstraintEvaluator();
    }

    public Solution createIntegratedSolution() {
        Solution solution = new Solution();
        solution.setConstructionMethod("INTEGRATED_HEURISTIC");
        solution.setCreationTime(System.currentTimeMillis());
        return solution;
    }

    public void addMonthlyAssignments(Solution targetSolution, Solution monthlySource) {
        LoggingUtils.logInfo("Adding monthly assignments to integrated solution");

        // Copy all monthly assignments
        Map<String, Map<String, Set<Integer>>> monthlyAssignments = monthlySource.getMonthlyAssignments();

        for (Map.Entry<String, Map<String, Set<Integer>>> anaesthetistEntry : monthlyAssignments.entrySet()) {
            String anaesthetistId = anaesthetistEntry.getKey();

            for (Map.Entry<String, Set<Integer>> workstationEntry : anaesthetistEntry.getValue().entrySet()) {
                String workstationId = workstationEntry.getKey();

                for (Integer dayNumber : workstationEntry.getValue()) {
                    targetSolution.assignMonthly(anaesthetistId, workstationId, dayNumber);
                }
            }
        }

        // Copy rest day restrictions
        Map<String, Set<Integer>> restDayRestrictions = monthlySource.getRestDayRestrictions();
        for (Map.Entry<String, Set<Integer>> entry : restDayRestrictions.entrySet()) {
            String anaesthetistId = entry.getKey();
            for (Integer dayNumber : entry.getValue()) {
                targetSolution.addRestDayRestriction(anaesthetistId, dayNumber);
            }
        }

        // Copy monthly constraint violations
        Map<String, Integer> monthlyViolations = monthlySource.getConstraintViolations();
        for (Map.Entry<String, Integer> entry : monthlyViolations.entrySet()) {
            targetSolution.addConstraintViolation("MONTHLY_" + entry.getKey(), entry.getValue());
        }

        LoggingUtils.logInfo("Monthly assignments added: " +
                countTotalMonthlyAssignments(targetSolution) + " total assignments");
    }

    public void addWeeklyAssignments(Solution targetSolution, Solution weeklySource, int week) {
        LoggingUtils.logInfo("Adding weekly assignments for week " + week + " to integrated solution");

        // Get weekly assignments for the specific week
        Map<String, Map<String, Map<Integer, Boolean>>> weeklyAssignments = weeklySource.getWeeklyAssignments(week);

        for (Map.Entry<String, Map<String, Map<Integer, Boolean>>> anaesthetistEntry : weeklyAssignments.entrySet()) {
            String anaesthetistId = anaesthetistEntry.getKey();

            for (Map.Entry<String, Map<Integer, Boolean>> workstationEntry : anaesthetistEntry.getValue().entrySet()) {
                String workstationId = workstationEntry.getKey();

                for (Map.Entry<Integer, Boolean> dayEntry : workstationEntry.getValue().entrySet()) {
                    if (dayEntry.getValue()) { // If assigned
                        Integer dayNumber = dayEntry.getKey();
                        targetSolution.assignWeekly(anaesthetistId, workstationId, dayNumber, week);
                    }
                }
            }
        }

        // Copy weekly constraint violations
        Map<String, Integer> weeklyViolations = weeklySource.getConstraintViolations();
        for (Map.Entry<String, Integer> entry : weeklyViolations.entrySet()) {
            if (entry.getKey().startsWith("WEEKLY_")) {
                targetSolution.addConstraintViolation("WEEK" + week + "_" + entry.getKey(), entry.getValue());
            }
        }

        LoggingUtils.logInfo("Weekly assignments added for week " + week + ": " +
                countWeeklyAssignments(targetSolution, week) + " assignments");
    }

    public Solution finalizeIntegratedSolution(Solution solution, ProblemInstance instance) {
        LoggingUtils.logInfo("Finalizing integrated solution");

        // 1. Validate all hard constraints
        List<ConstraintViolation> hardViolations = hardConstraintChecker.checkAllHardConstraints(solution, instance);

        if (!hardViolations.isEmpty()) {
            LoggingUtils.logWarning("Hard constraint violations in integrated solution: " + hardViolations.size());
            solution = attemptIntegratedConstraintRepair(solution, instance, hardViolations);
        } else {
            LoggingUtils.logInfo("All hard constraints satisfied in integrated solution");
        }

        // 2. Evaluate all soft constraints
        List<ConstraintViolation> softViolations = softConstraintEvaluator.evaluateAllSoftConstraints(solution,
                instance);
        LoggingUtils.logInfo("Soft constraint violations in integrated solution: " + softViolations.size());

        // 3. Calculate final objective value
        double totalPenalty = softConstraintEvaluator.calculateTotalSoftConstraintPenalty(solution, instance);
        solution.setObjectiveValue(totalPenalty);

        // 4. Update constraint violation counts
        Map<ConstraintType, Integer> violationCounts = softConstraintEvaluator
                .getSoftConstraintViolationCounts(solution, instance);
        for (Map.Entry<ConstraintType, Integer> entry : violationCounts.entrySet()) {
            solution.addConstraintViolation("FINAL_" + entry.getKey().name(), entry.getValue());
        }

        // 5. Set solution metadata
        solution.setHardConstraintViolations(hardViolations.size());
        solution.setSoftConstraintViolations(softViolations.size());
        solution.setFeasible(hardViolations.isEmpty());

        // 6. Calculate solution statistics
        calculateSolutionStatistics(solution, instance);

        LoggingUtils.logInfo("Integrated solution finalized:");
        LoggingUtils.logInfo("  Objective value: " + totalPenalty);
        LoggingUtils.logInfo("  Hard violations: " + hardViolations.size());
        LoggingUtils.logInfo("  Soft violations: " + softViolations.size());
        LoggingUtils.logInfo("  Feasible: " + solution.isFeasible());

        return solution;
    }

    private Solution attemptIntegratedConstraintRepair(Solution solution, ProblemInstance instance,
            List<ConstraintViolation> violations) {

        LoggingUtils.logInfo("Attempting integrated constraint repair");

        // Group violations by type for targeted repair
        Map<ConstraintType, List<ConstraintViolation>> violationsByType = new HashMap<>();
        for (ConstraintViolation violation : violations) {
            violationsByType.computeIfAbsent(violation.getConstraintType(), k -> new ArrayList<>()).add(violation);
        }

        // Repair in order of priority
        ConstraintType[] repairOrder = {
                ConstraintType.HC2, // Availability (easiest)
                ConstraintType.HC9, // Invalid combinations
                ConstraintType.HC8, // Daily workload
                ConstraintType.HC6, // Rest day after SGOT
                ConstraintType.HC3, // SGOT consecutive
                ConstraintType.HC10, // Shift succession
                ConstraintType.HC7, // Weekend pairing
                ConstraintType.HC11, // Mandatory pairings
                ConstraintType.HC1 // Coverage (hardest)
        };

        for (ConstraintType type : repairOrder) {
            if (violationsByType.containsKey(type)) {
                solution = repairIntegratedConstraintType(solution, instance, type, violationsByType.get(type));

                // Re-check to see if repair was successful
                List<ConstraintViolation> remainingViolations = hardConstraintChecker.checkAllHardConstraints(solution,
                        instance);
                long currentTypeViolations = remainingViolations.stream()
                        .filter(v -> v.getConstraintType() == type)
                        .count();

                LoggingUtils.logInfo("Repair attempt for " + type + ": " +
                        violationsByType.get(type).size() + " -> " + currentTypeViolations + " violations");
            }
        }

        // Final verification
        List<ConstraintViolation> finalViolations = hardConstraintChecker.checkAllHardConstraints(solution, instance);
        LoggingUtils.logInfo("Integrated constraint repair completed: " +
                violations.size() + " -> " + finalViolations.size() + " violations");

        return solution;
    }

    private Solution repairIntegratedConstraintType(Solution solution, ProblemInstance instance,
            ConstraintType constraintType,
            List<ConstraintViolation> violations) {

        switch (constraintType) {
            case HC1:
                return repairIntegratedCoverageViolations(solution, instance, violations);
            case HC2:
                return repairIntegratedAvailabilityViolations(solution, instance, violations);
            case HC8:
                return repairIntegratedWorkloadViolations(solution, instance, violations);
            case HC9:
                return repairIntegratedInvalidCombinations(solution, instance, violations);
            default:
                LoggingUtils.logWarning("No integrated repair method for constraint type: " + constraintType);
                return solution;
        }
    }

    private Solution repairIntegratedCoverageViolations(Solution solution, ProblemInstance instance,
            List<ConstraintViolation> violations) {
        // HC1: Coverage violations - try to reassign or find alternative assignments
        for (ConstraintViolation violation : violations) {
            String workstationId = violation.getWorkstationId();
            int dayNumber = violation.getDayNumber();

            if (workstationId != null && dayNumber > 0) {
                // Attempt to fix coverage by finding alternative assignments
                LoggingUtils.logDebug("Attempting coverage repair for " + workstationId + " on day " + dayNumber);
                // Detailed repair logic would go here
            }
        }
        return solution;
    }

    private Solution repairIntegratedAvailabilityViolations(Solution solution, ProblemInstance instance,
            List<ConstraintViolation> violations) {
        // HC2: Remove assignments for unavailable anaesthetists
        for (ConstraintViolation violation : violations) {
            String anaesthetistId = violation.getAnaesthetistId();
            int dayNumber = violation.getDayNumber();

            if (anaesthetistId != null && dayNumber > 0) {
                // Remove all assignments for this anaesthetist on this day
                LoggingUtils.logDebug("Removing assignments for unavailable anaesthetist: " +
                        anaesthetistId + " on day " + dayNumber);

                // Remove monthly assignments
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    if (solution.isAssignedMonthly(anaesthetistId, workstation.getId(), dayNumber)) {
                        // Would call solution.removeMonthlyAssignment() if implemented
                        LoggingUtils.logDebug("Would remove monthly assignment: " + anaesthetistId +
                                " from " + workstation.getId() + " on day " + dayNumber);
                    }
                }

                // Remove weekly assignments
                PlanningDay day = instance.getPlanningDay(dayNumber);
                if (day != null) {
                    final int currentWeek = day.getWeek(); // Make effectively final
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        if (solution.isAssignedWeekly(anaesthetistId, workstation.getId(), dayNumber, currentWeek)) {
                            // Would call solution.removeWeeklyAssignment() if implemented
                            LoggingUtils.logDebug("Would remove weekly assignment: " + anaesthetistId +
                                    " from " + workstation.getId() + " on day " + dayNumber + " (week " + currentWeek
                                    + ")");
                        }
                    }
                }
            }
        }
        return solution;
    }

    private Solution repairIntegratedWorkloadViolations(Solution solution, ProblemInstance instance,
            List<ConstraintViolation> violations) {
        // HC8: Remove assignments that exceed daily workload limits
        for (ConstraintViolation violation : violations) {
            String anaesthetistId = violation.getAnaesthetistId();
            int dayNumber = violation.getDayNumber();

            if (anaesthetistId != null && dayNumber > 0) {
                // Calculate current workload and remove lowest priority assignments
                double currentWorkload = calculateIntegratedDayWorkload(solution, instance, anaesthetistId, dayNumber);

                LoggingUtils.logDebug("Repairing workload violation for " + anaesthetistId +
                        " on day " + dayNumber + " (current: " + currentWorkload +
                        ", limit: " + instance.getMaxDailyWorkload() + ")");

                // Detailed workload repair logic would go here
            }
        }
        return solution;
    }

    private Solution repairIntegratedInvalidCombinations(Solution solution, ProblemInstance instance,
            List<ConstraintViolation> violations) {
        // HC9: Remove invalid assignment combinations
        for (ConstraintViolation violation : violations) {
            String anaesthetistId = violation.getAnaesthetistId();
            String workstationId = violation.getWorkstationId();
            int dayNumber = violation.getDayNumber();

            if (anaesthetistId != null && workstationId != null && dayNumber > 0) {
                LoggingUtils.logDebug("Repairing invalid combination: " + anaesthetistId +
                        " assigned to " + workstationId + " on day " + dayNumber);

                // Remove the specific invalid assignment
                // Detailed repair logic would go here
            }
        }
        return solution;
    }

    private double calculateIntegratedDayWorkload(Solution solution, ProblemInstance instance,
            String anaesthetistId, int dayNumber) {
        double workload = 0.0;

        // Add monthly workload
        for (Workstation workstation : instance.getMonthlyWorkstations()) {
            if (solution.isAssignedMonthly(anaesthetistId, workstation.getId(), dayNumber)) {
                workload += workstation.getWeight();
            }
        }

        // Add weekly workload
        PlanningDay day = instance.getPlanningDay(dayNumber);
        if (day != null) {
            final int currentWeek = day.getWeek(); // Make effectively final
            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                if (solution.isAssignedWeekly(anaesthetistId, workstation.getId(), dayNumber, currentWeek)) {
                    workload += workstation.getWeight();
                }
            }
        }

        return workload;
    }

    private void calculateSolutionStatistics(Solution solution, ProblemInstance instance) {
        // Calculate coverage statistics
        int totalDemand = 0;
        int totalAssigned = 0;

        // Monthly coverage
        for (Workstation workstation : instance.getMonthlyWorkstations()) {
            for (PlanningDay day : instance.getPlanningDays()) {
                int demand = instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());
                totalDemand += demand;

                long assigned = instance.getAnaesthetists().stream()
                        .filter(a -> solution.isAssignedMonthly(a.getId(), workstation.getId(), day.getDayNumber()))
                        .count();
                totalAssigned += assigned;
            }
        }

        // Weekly coverage - Fix the effectively final issue
        for (int weekNum = 1; weekNum <= 4; weekNum++) {
            final int currentWeek = weekNum; // Make effectively final
            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                for (PlanningDay day : instance.getWeekDays(currentWeek)) {
                    int demand = instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());
                    totalDemand += demand;

                    long assigned = instance.getAnaesthetists().stream()
                            .filter(a -> solution.isAssignedWeekly(a.getId(), workstation.getId(),
                                    day.getDayNumber(), currentWeek))
                            .count();
                    totalAssigned += assigned;
                }
            }
        }

        double coverageRate = totalDemand > 0 ? (double) totalAssigned / totalDemand : 0.0;
        solution.addStatistic("coverage_rate", coverageRate);
        solution.addStatistic("total_demand", totalDemand);
        solution.addStatistic("total_assigned", totalAssigned);

        LoggingUtils.logInfo("Solution statistics calculated: " +
                String.format("Coverage: %.2f%% (%d/%d)", coverageRate * 100, totalAssigned, totalDemand));
    }

    // Helper methods for counting assignments
    private int countTotalMonthlyAssignments(Solution solution) {
        int count = 0;
        Map<String, Map<String, Set<Integer>>> monthlyAssignments = solution.getMonthlyAssignments();

        for (Map<String, Set<Integer>> anaesthetistAssignments : monthlyAssignments.values()) {
            for (Set<Integer> workstationDays : anaesthetistAssignments.values()) {
                count += workstationDays.size();
            }
        }

        return count;
    }

    private int countWeeklyAssignments(Solution solution, int week) {
        int count = 0;
        Map<String, Map<String, Map<Integer, Boolean>>> weekData = solution.getWeeklyAssignments(week);

        for (Map<String, Map<Integer, Boolean>> anaesthetistData : weekData.values()) {
            for (Map<Integer, Boolean> workstationAssignments : anaesthetistData.values()) {
                count += (int) workstationAssignments.values().stream()
                        .mapToLong(assigned -> assigned ? 1 : 0)
                        .sum();
            }
        }

        return count;
    }
}