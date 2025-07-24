// File: src/main/java/com/arp/main/DiagnosticRunner.java
package com.arp_1.main;

import com.arp_1.core.data.*;
import com.arp_1.core.models.Anaesthetist;
import com.arp_1.core.models.PlanningDay;
import com.arp_1.core.models.Solution;
import com.arp_1.core.models.Workstation;
import com.arp_1.core.constraints.*;
import com.arp_1.heuristics.strategies.LocationAwareSequentialConstruction;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Diagnostic runner to identify why solutions are infeasible
 */
public class DiagnosticRunner {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java DiagnosticRunner <data_path>");
            return;
        }

        try {
            String dataPath = args[0];

            // Load problem instance
            LoggingUtils.logInfo("Loading problem instance from: " + dataPath);
            ProblemInstance instance = DataLoader.loadProblemInstance(dataPath);
            ValidationUtils.validateProblemInstance(instance);
            ValidationUtils.printProblemInstanceSummary(instance);

            // Create simple heuristic
            LocationAwareSequentialConstruction heuristic = LocationAwareSequentialConstruction.createDeterministic();

            // Generate solution
            LoggingUtils.logInfo("Generating solution...");
            Solution solution = heuristic.constructSolution(instance);

            // Detailed diagnostics
            performDetailedDiagnostics(solution, instance);

        } catch (Exception e) {
            LoggingUtils.logError("Diagnostic failed: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private static void performDetailedDiagnostics(Solution solution, ProblemInstance instance) {
        LoggingUtils.logSectionHeader("DETAILED SOLUTION DIAGNOSTICS");

        // Basic solution info
        LoggingUtils.logInfo("Solution Objective: " + solution.getObjectiveValue());
        LoggingUtils.logInfo("Solution Feasible: " + solution.isFeasible());
        LoggingUtils.logInfo("Computation Time: " + solution.getComputationTime() + "ms");

        // Check hard constraints
        HardConstraintChecker hardChecker = new HardConstraintChecker();
        List<ConstraintViolation> hardViolations = hardChecker.checkAllHardConstraints(solution, instance);

        LoggingUtils.logInfo("Hard Constraint Violations: " + hardViolations.size());

        if (!hardViolations.isEmpty()) {
            LoggingUtils.logSeparator();
            LoggingUtils.logInfo("HARD CONSTRAINT VIOLATIONS DETAILS:");

            // Group violations by type
            Map<ConstraintType, List<ConstraintViolation>> violationsByType = new HashMap<>();
            for (ConstraintViolation violation : hardViolations) {
                violationsByType.computeIfAbsent(violation.getConstraintType(), k -> new ArrayList<>())
                        .add(violation);
            }

            // Print violations by type
            for (Map.Entry<ConstraintType, List<ConstraintViolation>> entry : violationsByType.entrySet()) {
                ConstraintType type = entry.getKey();
                List<ConstraintViolation> violations = entry.getValue();

                LoggingUtils.logInfo(type + " (" + type.getDescription() + "): " + violations.size() + " violations");

                // Show first few violations as examples
                int showCount = Math.min(3, violations.size());
                for (int i = 0; i < showCount; i++) {
                    LoggingUtils.logInfo("  Example: " + violations.get(i).getDescription());
                }

                if (violations.size() > showCount) {
                    LoggingUtils.logInfo("  ... and " + (violations.size() - showCount) + " more");
                }
            }
        }

        // Check coverage
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("COVERAGE ANALYSIS:");

        int totalDemand = 0;
        int totalAssigned = 0;
        int uncoveredDays = 0;

        // Monthly workstations coverage
        for (Workstation workstation : instance.getMonthlyWorkstations()) {
            int workstationUncovered = 0;

            for (PlanningDay day : instance.getPlanningDays()) {
                int required = instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());
                totalDemand += required;

                int assigned = 0;
                for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                    if (solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), day.getDayNumber())) {
                        assigned++;
                    }
                }
                totalAssigned += Math.min(assigned, required);

                if (assigned < required) {
                    workstationUncovered++;
                    uncoveredDays++;
                }
            }

            if (workstationUncovered > 0) {
                LoggingUtils.logInfo("  " + workstation.getId() + ": " + workstationUncovered + " uncovered days");
            }
        }

        double coverageRate = totalDemand > 0 ? (double) totalAssigned / totalDemand : 0.0;
        LoggingUtils.logInfo("Overall Coverage Rate: " + String.format("%.1f%%", coverageRate * 100));
        LoggingUtils.logInfo("Total Uncovered Days: " + uncoveredDays);

        // Assignment statistics
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("ASSIGNMENT STATISTICS:");

        int totalMonthlyAssignments = 0;
        int totalWeeklyAssignments = 0;

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int monthlyCount = 0;
            int weeklyCount = 0;

            // Count monthly assignments
            for (Workstation workstation : instance.getMonthlyWorkstations()) {
                monthlyCount += solution.countMonthlyAssignments(anaesthetist.getId(), workstation.getId());
            }

            // Count weekly assignments
            for (int week = 1; week <= 4; week++) {
                for (Workstation workstation : instance.getWeeklyWorkstations()) {
                    weeklyCount += solution.countWeeklyAssignments(anaesthetist.getId(), workstation.getId(), week);
                }
            }

            totalMonthlyAssignments += monthlyCount;
            totalWeeklyAssignments += weeklyCount;
        }

        LoggingUtils.logInfo("Total Monthly Assignments: " + totalMonthlyAssignments);
        LoggingUtils.logInfo("Total Weekly Assignments: " + totalWeeklyAssignments);
        LoggingUtils.logInfo("Total Demand: " + totalDemand);

        // Check for null/empty assignments
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("SOLUTION STRUCTURE CHECK:");

        boolean hasMonthlyData = solution.getMonthlyAssignments() != null
                && !solution.getMonthlyAssignments().isEmpty();

        // Check weekly data for all weeks
        boolean hasWeeklyData = false;
        for (int week = 1; week <= 4; week++) {
            Map<String, Map<String, Map<Integer, Boolean>>> weekData = solution.getWeeklyAssignments(week);
            if (weekData != null && !weekData.isEmpty()) {
                hasWeeklyData = true;
                break;
            }
        }

        LoggingUtils.logInfo("Has Monthly Data: " + hasMonthlyData);
        LoggingUtils.logInfo("Has Weekly Data: " + hasWeeklyData);

        // Weekly workstations coverage
        for (int week = 1; week <= 4; week++) {
            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                int workstationUncovered = 0;

                for (PlanningDay day : instance.getWeekDays(week)) {
                    int required = instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());
                    totalDemand += required;

                    int assigned = 0;
                    for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                day.getDayNumber(), week)) {
                            assigned++;
                        }
                    }
                    totalAssigned += Math.min(assigned, required);

                    if (assigned < required) {
                        workstationUncovered++;
                        uncoveredDays++;
                    }
                }

                if (workstationUncovered > 0) {
                    LoggingUtils.logInfo("  " + workstation.getId() + " (Week " + week + "): " +
                            workstationUncovered + " uncovered days");
                }
            }
        }

        // Identify most problematic constraints
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("MOST PROBLEMATIC CONSTRAINTS:");

        if (!hardViolations.isEmpty()) {
            Map<ConstraintType, Integer> violationCounts = new HashMap<>();
            for (ConstraintViolation violation : hardViolations) {
                violationCounts.merge(violation.getConstraintType(), violation.getViolationCount(), Integer::sum);
            }

            violationCounts.entrySet().stream()
                    .sorted(Map.Entry.<ConstraintType, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> {
                        LoggingUtils.logInfo("  " + entry.getKey() + ": " + entry.getValue() + " total violations");
                        LoggingUtils.logInfo("    " + entry.getKey().getDescription());
                    });
        }

        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("DIAGNOSTIC COMPLETE");
    }
}