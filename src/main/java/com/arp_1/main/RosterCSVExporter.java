// File: src/main/java/com/arp/main/RosterCSVExporter.java
package com.arp_1.main;

import com.arp_1.core.data.*;
import com.arp_1.core.models.Anaesthetist;
import com.arp_1.core.models.Solution;
import com.arp_1.core.models.Workstation;
import com.arp_1.heuristics.strategies.LocationAwareSequentialConstruction;
import com.arp_1.utils.LoggingUtils;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Export roster to CSV files for easy viewing and analysis
 */
public class RosterCSVExporter {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java RosterCSVExporter <data_path> <output_path>");
            System.out.println("Example: java RosterCSVExporter data/month1/ results/roster_export/");
            return;
        }

        try {
            String dataPath = args[0];
            String outputPath = args[1];

            // Create output directory
            new File(outputPath).mkdirs();

            // Load problem instance
            LoggingUtils.logInfo("Loading problem instance from: " + dataPath);
            ProblemInstance instance = DataLoader.loadProblemInstance(dataPath);
            ValidationUtils.validateProblemInstance(instance);

            // Generate solution
            LoggingUtils.logInfo("Generating solution...");
            LocationAwareSequentialConstruction heuristic = LocationAwareSequentialConstruction.createDeterministic();
            Solution solution = heuristic.constructSolution(instance);

            // Export to CSV files
            LoggingUtils.logInfo("Exporting roster to CSV files in: " + outputPath);
            exportRosterToCSV(solution, instance, outputPath);

            LoggingUtils.logInfo("Roster export completed successfully!");
            System.out.println("\nGenerated CSV files:");
            System.out.println("  " + outputPath + "monthly_roster.csv");
            System.out.println("  " + outputPath + "weekly_roster_week1.csv");
            System.out.println("  " + outputPath + "weekly_roster_week2.csv");
            System.out.println("  " + outputPath + "weekly_roster_week3.csv");
            System.out.println("  " + outputPath + "weekly_roster_week4.csv");
            System.out.println("  " + outputPath + "workload_summary.csv");
            System.out.println("  " + outputPath + "coverage_analysis.csv");
            System.out.println("  " + outputPath + "solution_summary.csv");

        } catch (Exception e) {
            LoggingUtils.logError("Roster CSV export failed: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    /**
     * Export complete roster to multiple CSV files
     */
    public static void exportRosterToCSV(Solution solution, ProblemInstance instance, String outputPath)
            throws IOException {
        // Ensure output path ends with separator
        if (!outputPath.endsWith(File.separator)) {
            outputPath += File.separator;
        }

        // Export different views
        exportMonthlyRosterCSV(solution, instance, outputPath + "monthly_roster.csv");
        exportWeeklyRosterCSV(solution, instance, outputPath);
        exportWorkloadSummaryCSV(solution, instance, outputPath + "workload_summary.csv");
        exportCoverageAnalysisCSV(solution, instance, outputPath + "coverage_analysis.csv");
        exportSolutionSummaryCSV(solution, instance, outputPath + "solution_summary.csv");
    }

    /**
     * Export monthly roster - days as rows, workstations as columns
     */
    private static void exportMonthlyRosterCSV(Solution solution, ProblemInstance instance, String filename)
            throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(filename));

        // Get monthly workstations
        List<Workstation> monthlyWorkstations = instance.getMonthlyWorkstations();
        List<String> workstationIds = monthlyWorkstations.stream()
                .map(Workstation::getId)
                .collect(Collectors.toList());

        // Write header
        writer.print("Day");
        for (String workstationId : workstationIds) {
            writer.print("," + workstationId + "_Assigned");
            writer.print("," + workstationId + "_Count");
        }
        writer.println();

        // Write data for each day (1-28)
        for (int day = 1; day <= 28; day++) {
            writer.print(day);

            for (String workstationId : workstationIds) {
                // Get assigned anaesthetists for this day/workstation
                List<String> assigned = new ArrayList<>();
                int count = 0;

                for (String anaesthetistId : solution.getAllAssignedAnaesthetists()) {
                    if (solution.isAssignedMonthly(anaesthetistId, workstationId, day)) {
                        assigned.add(anaesthetistId);
                        count++;
                    }
                }

                // Write assigned anaesthetists (comma-separated within quotes)
                String assignedStr = assigned.isEmpty() ? "" : "\"" + String.join(";", assigned) + "\"";
                writer.print("," + assignedStr);
                writer.print("," + count);
            }

            writer.println();
        }

        writer.close();
    }

    /**
     * Export weekly rosters - separate file for each week
     */
    private static void exportWeeklyRosterCSV(Solution solution, ProblemInstance instance, String outputPath)
            throws IOException {
        List<Workstation> weeklyWorkstations = instance.getWeeklyWorkstations();
        List<String> workstationIds = weeklyWorkstations.stream()
                .map(Workstation::getId)
                .collect(Collectors.toList());

        for (int week = 1; week <= 4; week++) {
            String filename = outputPath + "weekly_roster_week" + week + ".csv";
            PrintWriter writer = new PrintWriter(new FileWriter(filename));

            // Write header
            writer.print("Day");
            for (String workstationId : workstationIds) {
                writer.print("," + workstationId + "_Assigned");
                writer.print("," + workstationId + "_Count");
            }
            writer.println();

            // Write data for each day in this week
            int startDay = (week - 1) * 7 + 1;
            int endDay = Math.min(week * 7, 28);

            for (int day = startDay; day <= endDay; day++) {
                writer.print(day);

                for (String workstationId : workstationIds) {
                    // Get assigned anaesthetists for this day/workstation/week
                    List<String> assigned = new ArrayList<>();
                    int count = 0;

                    for (String anaesthetistId : solution.getAllAssignedAnaesthetists()) {
                        if (solution.isAssignedWeekly(anaesthetistId, workstationId, day, week)) {
                            assigned.add(anaesthetistId);
                            count++;
                        }
                    }

                    // Write assigned anaesthetists
                    String assignedStr = assigned.isEmpty() ? "" : "\"" + String.join(";", assigned) + "\"";
                    writer.print("," + assignedStr);
                    writer.print("," + count);
                }

                writer.println();
            }

            writer.close();
        }
    }

    /**
     * Export workload summary - anaesthetists and their assignments
     */
    private static void exportWorkloadSummaryCSV(Solution solution, ProblemInstance instance, String filename)
            throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(filename));

        // Write header
        writer.println(
                "Anaesthetist_ID,Monthly_Assignments,Weekly_Week1,Weekly_Week2,Weekly_Week3,Weekly_Week4,Total_Weekly,Total_All,Status");

        // Calculate workload for each anaesthetist
        Set<String> allAnaesthetists = solution.getAllAssignedAnaesthetists();

        // Include all anaesthetists from instance, not just assigned ones
        Set<String> allInstanceAnaesthetists = instance.getAnaesthetists().stream()
                .map(Anaesthetist::getId)
                .collect(Collectors.toSet());

        allInstanceAnaesthetists.addAll(allAnaesthetists);

        List<String> sortedAnaesthetists = new ArrayList<>(allInstanceAnaesthetists);
        Collections.sort(sortedAnaesthetists);

        for (String anaesthetistId : sortedAnaesthetists) {
            int monthlyCount = solution.countTotalMonthlyAssignments(anaesthetistId);

            int week1Count = solution.countTotalWeeklyAssignments(anaesthetistId, 1);
            int week2Count = solution.countTotalWeeklyAssignments(anaesthetistId, 2);
            int week3Count = solution.countTotalWeeklyAssignments(anaesthetistId, 3);
            int week4Count = solution.countTotalWeeklyAssignments(anaesthetistId, 4);

            int totalWeekly = week1Count + week2Count + week3Count + week4Count;
            int totalAll = monthlyCount + totalWeekly;

            String status = totalAll == 0 ? "UNUSED"
                    : totalAll < 5 ? "MINIMAL"
                            : totalAll < 15 ? "LIGHT"
                                    : totalAll < 25 ? "NORMAL" : totalAll < 35 ? "HEAVY" : "OVERLOADED";

            writer.printf("%s,%d,%d,%d,%d,%d,%d,%d,%s\n",
                    anaesthetistId, monthlyCount, week1Count, week2Count, week3Count, week4Count,
                    totalWeekly, totalAll, status);
        }

        writer.close();
    }

    /**
     * Export coverage analysis - workstations and their assignment counts
     */
    private static void exportCoverageAnalysisCSV(Solution solution, ProblemInstance instance, String filename)
            throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(filename));

        // Write header
        writer.println(
                "Workstation_ID,Type,Total_Assignments,Days_With_Assignments,Avg_Per_Day,Max_Per_Day,Min_Per_Day,Status");

        // Analyze monthly workstations
        for (Workstation workstation : instance.getMonthlyWorkstations()) {
            String workstationId = workstation.getId();

            int totalAssignments = 0;
            int daysWithAssignments = 0;
            int maxPerDay = 0;
            int minPerDay = Integer.MAX_VALUE;

            for (int day = 1; day <= 28; day++) {
                int dayCount = 0;
                for (String anaesthetistId : solution.getAllAssignedAnaesthetists()) {
                    if (solution.isAssignedMonthly(anaesthetistId, workstationId, day)) {
                        dayCount++;
                    }
                }

                if (dayCount > 0) {
                    daysWithAssignments++;
                    totalAssignments += dayCount;
                    maxPerDay = Math.max(maxPerDay, dayCount);
                    minPerDay = Math.min(minPerDay, dayCount);
                }
            }

            if (minPerDay == Integer.MAX_VALUE)
                minPerDay = 0;
            double avgPerDay = daysWithAssignments > 0 ? (double) totalAssignments / daysWithAssignments : 0;

            String status = daysWithAssignments == 0 ? "NO_ASSIGNMENTS"
                    : maxPerDay > 2 ? "OVER_ASSIGNMENT" : daysWithAssignments < 20 ? "UNDER_COVERAGE" : "NORMAL";

            writer.printf("%s,Monthly,%d,%d,%.2f,%d,%d,%s\n",
                    workstationId, totalAssignments, daysWithAssignments, avgPerDay, maxPerDay, minPerDay, status);
        }

        // Analyze weekly workstations
        for (Workstation workstation : instance.getWeeklyWorkstations()) {
            String workstationId = workstation.getId();

            for (int week = 1; week <= 4; week++) {
                int totalAssignments = 0;
                int daysWithAssignments = 0;
                int maxPerDay = 0;
                int minPerDay = Integer.MAX_VALUE;

                int startDay = (week - 1) * 7 + 1;
                int endDay = Math.min(week * 7, 28);

                for (int day = startDay; day <= endDay; day++) {
                    int dayCount = 0;
                    for (String anaesthetistId : solution.getAllAssignedAnaesthetists()) {
                        if (solution.isAssignedWeekly(anaesthetistId, workstationId, day, week)) {
                            dayCount++;
                        }
                    }

                    if (dayCount > 0) {
                        daysWithAssignments++;
                        totalAssignments += dayCount;
                        maxPerDay = Math.max(maxPerDay, dayCount);
                        minPerDay = Math.min(minPerDay, dayCount);
                    }
                }

                if (minPerDay == Integer.MAX_VALUE)
                    minPerDay = 0;
                double avgPerDay = daysWithAssignments > 0 ? (double) totalAssignments / daysWithAssignments : 0;

                String status = daysWithAssignments == 0 ? "NO_ASSIGNMENTS"
                        : maxPerDay > 3 ? "OVER_ASSIGNMENT" : daysWithAssignments < 5 ? "UNDER_COVERAGE" : "NORMAL";

                writer.printf("%s_Week%d,Weekly,%d,%d,%.2f,%d,%d,%s\n",
                        workstationId, week, totalAssignments, daysWithAssignments, avgPerDay, maxPerDay, minPerDay,
                        status);
            }
        }

        writer.close();
    }

    /**
     * Export solution summary statistics
     */
    private static void exportSolutionSummaryCSV(Solution solution, ProblemInstance instance, String filename)
            throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(filename));

        // Write header
        writer.println("Metric,Value,Description");

        // Basic statistics
        writer.println("Total_Monthly_Assignments," + solution.getTotalMonthlyAssignments()
                + ",Total assignments to monthly workstations");
        writer.println("Total_Weekly_Assignments," + solution.getTotalWeeklyAssignments()
                + ",Total assignments to weekly workstations");
        writer.println("Total_All_Assignments,"
                + (solution.getTotalMonthlyAssignments() + solution.getTotalWeeklyAssignments())
                + ",Combined total assignments");
        writer.println("Objective_Value," + solution.getObjectiveValue() + ",Solution objective value (penalty score)");
        writer.println("Hard_Constraint_Violations," + solution.getHardConstraintViolations()
                + ",Number of hard constraint violations");
        writer.println("Soft_Constraint_Violations," + solution.getSoftConstraintViolations()
                + ",Number of soft constraint violations");
        writer.println("Feasible," + (solution.isFeasible() ? "YES" : "NO") + ",Whether solution is feasible");
        writer.println("Computation_Time_Ms," + solution.getComputationTime() + ",Time taken to generate solution");
        writer.println("Anaesthetists_Used," + solution.getAllAssignedAnaesthetists().size()
                + ",Number of anaesthetists with assignments");
        writer.println("Workstations_Used," + solution.getAllAssignedWorkstations().size()
                + ",Number of workstations with assignments");
        writer.println("Total_Anaesthetists," + instance.getAnaesthetists().size() + ",Total anaesthetists available");
        writer.println("Total_Workstations,"
                + (instance.getMonthlyWorkstations().size() + instance.getWeeklyWorkstations().size())
                + ",Total workstations defined");

        // Workload statistics
        List<Integer> workloads = new ArrayList<>();
        for (String anaesthetistId : solution.getAllAssignedAnaesthetists()) {
            int totalWorkload = solution.countTotalMonthlyAssignments(anaesthetistId);
            for (int week = 1; week <= 4; week++) {
                totalWorkload += solution.countTotalWeeklyAssignments(anaesthetistId, week);
            }
            workloads.add(totalWorkload);
        }

        if (!workloads.isEmpty()) {
            int minWorkload = Collections.min(workloads);
            int maxWorkload = Collections.max(workloads);
            double avgWorkload = workloads.stream().mapToInt(i -> i).average().orElse(0);

            writer.println("Min_Workload," + minWorkload + ",Minimum assignments per anaesthetist");
            writer.println("Max_Workload," + maxWorkload + ",Maximum assignments per anaesthetist");
            writer.println(
                    "Avg_Workload," + String.format("%.2f", avgWorkload) + ",Average assignments per anaesthetist");
            writer.println(
                    "Workload_Range," + (maxWorkload - minWorkload) + ",Difference between max and min workload");
        }

        writer.close();
    }
}