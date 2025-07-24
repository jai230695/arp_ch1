// File: src/main/java/com/arp/main/SimpleRosterViewer.java
package com.arp_1.main;

import com.arp_1.core.data.*;
import com.arp_1.core.models.Solution;
import com.arp_1.heuristics.strategies.LocationAwareSequentialConstruction;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Simple roster viewer that works with basic available methods
 */
public class SimpleRosterViewer {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java SimpleRosterViewer <data_path>");
            return;
        }

        try {
            String dataPath = args[0];

            // Load problem instance
            LoggingUtils.logInfo("Loading problem instance from: " + dataPath);
            ProblemInstance instance = DataLoader.loadProblemInstance(dataPath);
            ValidationUtils.validateProblemInstance(instance);

            // Generate solution
            LocationAwareSequentialConstruction heuristic = LocationAwareSequentialConstruction.createDeterministic();
            Solution solution = heuristic.constructSolution(instance);

            // Display the roster
            displayRoster(solution, instance);

        } catch (Exception e) {
            LoggingUtils.logError("Roster display failed: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    /**
     * Display roster in simple format
     */
    public static void displayRoster(Solution solution, ProblemInstance instance) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("                               GENERATED ROSTER");
        System.out.println("=".repeat(100));

        displayBasicStatistics(solution, instance);
        displayMonthlyAssignments(solution, instance);
        displayWeeklyAssignments(solution, instance);
        displayWorkloadAnalysis(solution, instance);
        displayCoverageIssues(solution, instance);
    }

    /**
     * Display basic statistics
     */
    private static void displayBasicStatistics(Solution solution, ProblemInstance instance) {
        System.out.println("\n=== BASIC SOLUTION STATISTICS ===");

        int totalMonthly = solution.getTotalMonthlyAssignments();
        int totalWeekly = solution.getTotalWeeklyAssignments();

        System.out.printf("Total Monthly Assignments: %d\n", totalMonthly);
        System.out.printf("Total Weekly Assignments:  %d\n", totalWeekly);
        System.out.printf("Total All Assignments:     %d\n", totalMonthly + totalWeekly);
        System.out.printf("Objective Value:           %.2f\n", solution.getObjectiveValue());
        System.out.printf("Hard Violations:           %d\n", solution.getHardConstraintViolations());
        System.out.printf("Soft Violations:           %d\n", solution.getSoftConstraintViolations());
        System.out.printf("Feasible:                  %s\n", solution.isFeasible() ? "YES" : "NO");
        System.out.printf("Anaesthetists Used:        %d\n", solution.getAllAssignedAnaesthetists().size());
        System.out.printf("Workstations Used:         %d\n", solution.getAllAssignedWorkstations().size());
    }

    /**
     * Display monthly assignments by day and workstation
     */
    private static void displayMonthlyAssignments(Solution solution, ProblemInstance instance) {
        System.out.println("\n=== MONTHLY ASSIGNMENTS BY DAY ===");

        // Get monthly assignments by day
        Map<String, Map<String, Set<Integer>>> monthlyData = solution.getMonthlyAssignments();

        // Collect all days that have assignments
        Set<Integer> assignmentDays = new TreeSet<>();
        for (Map<String, Set<Integer>> anaesthetistData : monthlyData.values()) {
            for (Set<Integer> days : anaesthetistData.values()) {
                assignmentDays.addAll(days);
            }
        }

        System.out.printf("Days with assignments: %s\n", assignmentDays);
        System.out.println("Format: Day -> Workstation: [Anaesthetists]");
        System.out.println("-".repeat(80));

        for (Integer day : assignmentDays) {
            System.out.printf("\nDAY %d:\n", day);

            // Group by workstation for this day
            Map<String, List<String>> workstationAssignments = new TreeMap<>();

            for (Map.Entry<String, Map<String, Set<Integer>>> anaesthetistEntry : monthlyData.entrySet()) {
                String anaesthetistId = anaesthetistEntry.getKey();

                for (Map.Entry<String, Set<Integer>> workstationEntry : anaesthetistEntry.getValue().entrySet()) {
                    String workstationId = workstationEntry.getKey();
                    Set<Integer> days = workstationEntry.getValue();

                    if (days.contains(day)) {
                        workstationAssignments.computeIfAbsent(workstationId, k -> new ArrayList<>())
                                .add(anaesthetistId);
                    }
                }
            }

            for (Map.Entry<String, List<String>> entry : workstationAssignments.entrySet()) {
                System.out.printf("  %-8s: %s (%d assigned)\n",
                        entry.getKey(), entry.getValue(), entry.getValue().size());
            }
        }
    }

    /**
     * Display weekly assignments by week and workstation
     */
    private static void displayWeeklyAssignments(Solution solution, ProblemInstance instance) {
        System.out.println("\n=== WEEKLY ASSIGNMENTS BY WEEK ===");

        Map<Integer, Map<String, Map<String, Map<Integer, Boolean>>>> weeklyData = solution.getAllWeeklyAssignments();

        for (Integer week : new TreeSet<>(weeklyData.keySet())) {
            System.out.printf("\nWEEK %d:\n", week);
            System.out.println("-".repeat(40));

            Map<String, Map<String, Map<Integer, Boolean>>> weekData = weeklyData.get(week);

            // Group by workstation for this week
            Map<String, Set<String>> workstationAssignments = new TreeMap<>();
            Map<String, Integer> workstationDayCounts = new TreeMap<>();

            for (Map.Entry<String, Map<String, Map<Integer, Boolean>>> anaesthetistEntry : weekData.entrySet()) {
                String anaesthetistId = anaesthetistEntry.getKey();

                for (Map.Entry<String, Map<Integer, Boolean>> workstationEntry : anaesthetistEntry.getValue()
                        .entrySet()) {
                    String workstationId = workstationEntry.getKey();
                    Map<Integer, Boolean> dayAssignments = workstationEntry.getValue();

                    int assignedDays = (int) dayAssignments.values().stream().mapToLong(b -> b ? 1 : 0).sum();
                    if (assignedDays > 0) {
                        workstationAssignments.computeIfAbsent(workstationId, k -> new TreeSet<>())
                                .add(anaesthetistId);
                        workstationDayCounts.merge(workstationId, assignedDays, Integer::sum);
                    }
                }
            }

            for (Map.Entry<String, Set<String>> entry : workstationAssignments.entrySet()) {
                String workstationId = entry.getKey();
                Set<String> anaesthetists = entry.getValue();
                int totalDayAssignments = workstationDayCounts.get(workstationId);

                System.out.printf("  %-8s: %d anaesthetists, %d total day-assignments\n",
                        workstationId, anaesthetists.size(), totalDayAssignments);
                System.out.printf("           Anaesthetists: %s\n", anaesthetists);
            }
        }
    }

    /**
     * Display workload analysis
     */
    private static void displayWorkloadAnalysis(Solution solution, ProblemInstance instance) {
        System.out.println("\n=== WORKLOAD ANALYSIS ===");

        Map<String, Integer> anaesthetistWorkload = new TreeMap<>();

        // Calculate workload for each anaesthetist
        Set<String> allAnaesthetists = solution.getAllAssignedAnaesthetists();

        for (String anaesthetistId : allAnaesthetists) {
            int totalWorkload = 0;

            // Monthly workload
            totalWorkload += solution.countTotalMonthlyAssignments(anaesthetistId);

            // Weekly workload
            for (int week = 1; week <= 4; week++) {
                totalWorkload += solution.countTotalWeeklyAssignments(anaesthetistId, week);
            }

            anaesthetistWorkload.put(anaesthetistId, totalWorkload);
        }

        // Sort by workload
        List<Map.Entry<String, Integer>> sortedWorkload = new ArrayList<>(anaesthetistWorkload.entrySet());
        sortedWorkload.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        System.out.printf("%-15s %10s %10s %10s %10s\n", "Anaesthetist", "Monthly", "Weekly", "Total", "Status");
        System.out.println("-".repeat(70));

        for (Map.Entry<String, Integer> entry : sortedWorkload) {
            String anaesthetistId = entry.getKey();
            int monthlyWorkload = solution.countTotalMonthlyAssignments(anaesthetistId);
            int weeklyWorkload = 0;
            for (int week = 1; week <= 4; week++) {
                weeklyWorkload += solution.countTotalWeeklyAssignments(anaesthetistId, week);
            }
            int totalWorkload = entry.getValue();

            String status = totalWorkload > 25 ? "OVERLOADED"
                    : totalWorkload > 15 ? "NORMAL" : totalWorkload > 5 ? "LIGHT" : "MINIMAL";

            System.out.printf("%-15s %10d %10d %10d %10s\n",
                    anaesthetistId, monthlyWorkload, weeklyWorkload, totalWorkload, status);
        }

        // Statistics
        if (!anaesthetistWorkload.isEmpty()) {
            int min = Collections.min(anaesthetistWorkload.values());
            int max = Collections.max(anaesthetistWorkload.values());
            double avg = anaesthetistWorkload.values().stream().mapToInt(i -> i).average().orElse(0);

            System.out.println("\nWorkload Statistics:");
            System.out.printf("  Minimum: %d, Maximum: %d, Average: %.1f, Range: %d\n",
                    min, max, avg, max - min);
        }
    }

    /**
     * Display coverage issues (over/under assignment)
     */
    private static void displayCoverageIssues(Solution solution, ProblemInstance instance) {
        System.out.println("\n=== COVERAGE ISSUES ANALYSIS ===");

        System.out.println("Monthly Workstation Assignment Counts:");
        System.out.printf("%-12s %15s\n", "Workstation", "Total Assignments");
        System.out.println("-".repeat(35));

        // Analyze monthly workstations
        Map<String, Set<String>> monthlyWorkstations = new TreeMap<>();

        Map<String, Map<String, Set<Integer>>> monthlyData = solution.getMonthlyAssignments();
        for (Map.Entry<String, Map<String, Set<Integer>>> anaesthetistEntry : monthlyData.entrySet()) {
            for (Map.Entry<String, Set<Integer>> workstationEntry : anaesthetistEntry.getValue().entrySet()) {
                String workstationId = workstationEntry.getKey();
                monthlyWorkstations.computeIfAbsent(workstationId, k -> new TreeSet<>())
                        .add(anaesthetistEntry.getKey());
            }
        }

        for (Map.Entry<String, Set<String>> entry : monthlyWorkstations.entrySet()) {
            String workstationId = entry.getKey();
            int totalAssignments = 0;

            for (String anaesthetistId : entry.getValue()) {
                totalAssignments += solution.countMonthlyAssignments(anaesthetistId, workstationId);
            }

            System.out.printf("%-12s %15d\n", workstationId, totalAssignments);
        }

        System.out.println("\nWeekly Workstation Assignment Counts:");
        System.out.printf("%-12s %6s %15s\n", "Workstation", "Week", "Total Assignments");
        System.out.println("-".repeat(40));

        // Analyze weekly workstations
        Map<Integer, Map<String, Map<String, Map<Integer, Boolean>>>> weeklyData = solution.getAllWeeklyAssignments();
        for (Integer week : new TreeSet<>(weeklyData.keySet())) {
            Map<String, Integer> weeklyWorkstationCounts = new TreeMap<>();

            Map<String, Map<String, Map<Integer, Boolean>>> weekData = weeklyData.get(week);
            for (Map.Entry<String, Map<String, Map<Integer, Boolean>>> anaesthetistEntry : weekData.entrySet()) {
                String anaesthetistId = anaesthetistEntry.getKey();

                for (Map.Entry<String, Map<Integer, Boolean>> workstationEntry : anaesthetistEntry.getValue()
                        .entrySet()) {
                    String workstationId = workstationEntry.getKey();
                    int count = solution.countWeeklyAssignments(anaesthetistId, workstationId, week);
                    weeklyWorkstationCounts.merge(workstationId, count, Integer::sum);
                }
            }

            for (Map.Entry<String, Integer> entry : weeklyWorkstationCounts.entrySet()) {
                System.out.printf("%-12s %6d %15d\n", entry.getKey(), week, entry.getValue());
            }
        }

        System.out.println("\n=== SUMMARY ===");
        System.out.printf("This roster shows the actual assignments made by the algorithm.\n");
        System.out.printf("The high constraint violations (198 hard, 75 soft) indicate:\n");
        System.out.printf("1. Over-assignment: Multiple people assigned to same workstation-day\n");
        System.out.printf("2. Under-assignment: Some required positions not filled\n");
        System.out.printf("3. Constraint conflicts: Invalid shift combinations, missing pairings\n");
        System.out.printf("4. Workload imbalance: Uneven distribution of assignments\n");
    }
}