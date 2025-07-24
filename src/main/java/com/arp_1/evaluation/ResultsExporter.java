// File: src/main/java/com/arp/evaluation/ResultsExporter.java
package com.arp_1.evaluation;

import com.arp_1.core.models.*;
import com.arp_1.core.constraints.ConstraintType;
import com.arp_1.core.data.ProblemInstance;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ResultsExporter {

    public static void exportResults(Solution solution, ProblemInstance instance, String outputPath) {
        try {
            // Create output directory if it doesn't exist
            File outputDir = new File(outputPath);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

            // Export solution summary
            exportSolutionSummary(solution, instance, outputPath + "solution_summary_" + timestamp + ".txt");

            // Export detailed assignments
            exportDetailedAssignments(solution, instance, outputPath + "assignments_" + timestamp + ".csv");

            // Export constraint analysis
            exportConstraintAnalysis(solution, instance, outputPath + "constraints_" + timestamp + ".csv");

            // Export workload analysis
            exportWorkloadAnalysis(solution, instance, outputPath + "workload_" + timestamp + ".csv");

            System.out.println("Results exported to: " + outputPath);

        } catch (IOException e) {
            System.err.println("Error exporting results: " + e.getMessage());
        }
    }

    public static void exportMultipleRunsResults(Map<String, List<ExperimentResult>> allResults, String outputPath) {
        try {
            File outputDir = new File(outputPath);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

            // Export summary statistics
            exportSummaryStatistics(allResults, outputPath + "summary_statistics_" + timestamp + ".csv");

            // Export detailed results for each configuration
            for (Map.Entry<String, List<ExperimentResult>> entry : allResults.entrySet()) {
                String configName = entry.getKey();
                List<ExperimentResult> results = entry.getValue();
                exportConfigurationResults(configName, results, outputPath + configName + "_" + timestamp + ".csv");
            }

            // Export comparative analysis
            exportComparativeAnalysis(allResults, outputPath + "comparative_analysis_" + timestamp + ".csv");

            System.out.println("Multiple runs results exported to: " + outputPath);

        } catch (IOException e) {
            System.err.println("Error exporting multiple runs results: " + e.getMessage());
        }
    }

    private static void exportSolutionSummary(Solution solution, ProblemInstance instance, String filePath)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("ANAESTHETIST ROSTERING PROBLEM - SOLUTION SUMMARY");
            writer.println("=================================================");
            writer.println();

            // Basic solution info
            writer.printf("Objective Value: %.2f\n", solution.getObjectiveValue());
            writer.printf("Computation Time: %d ms\n", solution.getComputationTime());
            writer.printf("Hard Constraints: %s\n", solution.hasHardConstraintViolations() ? "VIOLATED" : "SATISFIED");
            writer.printf("Total Soft Violations: %d\n", solution.getTotalSoftConstraintViolations());
            writer.println();

            // Constraint breakdown
            writer.println("Constraint Violations:");
            writer.println("---------------------");
            Map<String, Integer> violations = solution.getConstraintViolations();
            for (String constraint : Arrays.asList("SC1", "SC2", "SC3", "SC4", "SC5", "SC6", "SC7", "SC8", "SC9",
                    "SC10")) {
                int count = violations.getOrDefault(constraint, 0);
                if (count > 0) {
                    writer.printf("%-4s: %3d violations\n", constraint, count);
                }
            }

            // MILP comparison
            writer.println();
            writer.println("MILP Comparison:");
            writer.println("----------------");
            writer.printf("MILP Benchmark: 70.0\n");
            writer.printf("Heuristic Result: %.2f\n", solution.getObjectiveValue());
            writer.printf("Performance Ratio: %.1f%% of MILP quality\n", 70.0 / solution.getObjectiveValue() * 100);

            // Workload summary
            writer.println();
            writer.println("Workload Summary:");
            writer.println("-----------------");

            Map<String, Integer> anaesthetistWorkloads = new HashMap<>();
            for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                int totalWorkload = 0;

                // Monthly assignments
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    totalWorkload += solution.countMonthlyAssignments(anaesthetist.getId(), workstation.getId());
                }

                // Weekly assignments
                for (int week = 1; week <= 4; week++) {
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        totalWorkload += solution.countWeeklyAssignments(anaesthetist.getId(), workstation.getId(),
                                week);
                    }
                }

                anaesthetistWorkloads.put(anaesthetist.getId(), totalWorkload);
            }

            Collection<Integer> workloads = anaesthetistWorkloads.values();
            int min = Collections.min(workloads);
            int max = Collections.max(workloads);
            double mean = workloads.stream().mapToInt(Integer::intValue).average().orElse(0.0);

            writer.printf("Workload Distribution: Min=%d, Max=%d, Mean=%.1f, Range=%d\n", min, max, mean, max - min);
        }
    }

    private static void exportDetailedAssignments(Solution solution, ProblemInstance instance, String filePath)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("Anaesthetist,Workstation,Day,Week,RosterType");

            // Monthly assignments
            for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    for (PlanningDay day : instance.getPlanningDays()) {
                        if (solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), day.getDayNumber())) {
                            writer.printf("%s,%s,%d,%d,Monthly\n",
                                    anaesthetist.getId(), workstation.getId(),
                                    day.getDayNumber(), day.getWeek());
                        }
                    }
                }
            }

            // Weekly assignments
            for (int week = 1; week <= 4; week++) {
                for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        for (PlanningDay day : instance.getWeekDays(week)) {
                            if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                    day.getDayNumber(), week)) {
                                writer.printf("%s,%s,%d,%d,Weekly\n",
                                        anaesthetist.getId(), workstation.getId(),
                                        day.getDayNumber(), week);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void exportConstraintAnalysis(Solution solution, ProblemInstance instance, String filePath)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("Constraint,Type,Violations,Weight,Penalty,Description");

            Map<String, Integer> violations = solution.getConstraintViolations();

            for (String constraint : Arrays.asList("SC1", "SC2", "SC3", "SC4", "SC5", "SC6", "SC7", "SC8", "SC9",
                    "SC10")) {
                int count = violations.getOrDefault(constraint, 0);
                ConstraintType type = ConstraintType.valueOf(constraint);
                int weight = type.getDefaultPenaltyWeight();
                int penalty = count * weight;

                writer.printf("%s,Soft,%d,%d,%d,\"%s\"\n",
                        constraint, count, weight, penalty, type.getDescription());
            }
        }
    }

    private static void exportWorkloadAnalysis(Solution solution, ProblemInstance instance, String filePath)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("Anaesthetist,Type,TotalAssignments,MonthlyAssignments,WeeklyAssignments");

            for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                int monthlyAssignments = 0;
                int weeklyAssignments = 0;

                // Count monthly assignments
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    monthlyAssignments += solution.countMonthlyAssignments(anaesthetist.getId(), workstation.getId());
                }

                // Count weekly assignments
                for (int week = 1; week <= 4; week++) {
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        weeklyAssignments += solution.countWeeklyAssignments(anaesthetist.getId(), workstation.getId(),
                                week);
                    }
                }

                int totalAssignments = monthlyAssignments + weeklyAssignments;

                writer.printf("%s,%s,%d,%d,%d\n",
                        anaesthetist.getId(), anaesthetist.getType(),
                        totalAssignments, monthlyAssignments, weeklyAssignments);
            }
        }
    }

    private static void exportSummaryStatistics(Map<String, List<ExperimentResult>> allResults, String filePath)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println(
                    "Configuration,Runs,MeanObjective,StdDev,MinObjective,MaxObjective,MeanTime,BestMILPRatio,AvgMILPRatio");

            for (Map.Entry<String, List<ExperimentResult>> entry : allResults.entrySet()) {
                String config = entry.getKey();
                List<ExperimentResult> results = entry.getValue();

                if (results.isEmpty())
                    continue;

                List<Double> objectives = results.stream()
                        .map(r -> r.getSolution().getObjectiveValue())
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

                List<Long> times = results.stream()
                        .map(ExperimentResult::getComputationTime)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

                double meanObj = objectives.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double minObj = objectives.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                double maxObj = objectives.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                double meanTime = times.stream().mapToLong(Long::longValue).average().orElse(0.0);

                double variance = objectives.stream()
                        .mapToDouble(v -> Math.pow(v - meanObj, 2))
                        .average().orElse(0.0);
                double stdDev = Math.sqrt(variance);

                double bestMILPRatio = minObj > 0 ? 70.0 / minObj * 100 : 0.0;
                double avgMILPRatio = meanObj > 0 ? 70.0 / meanObj * 100 : 0.0;

                writer.printf("%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.1f,%.1f\n",
                        config, results.size(), meanObj, stdDev, minObj, maxObj,
                        meanTime, bestMILPRatio, avgMILPRatio);
            }
        }
    }

    private static void exportConfigurationResults(String configName, List<ExperimentResult> results, String filePath)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("Run,Objective,ComputationTime,SC1,SC2,SC3,SC4,SC5,SC6,SC7,SC8,SC9,SC10");

            for (ExperimentResult result : results) {
                Solution solution = result.getSolution();
                Map<String, Integer> violations = solution.getConstraintViolations();

                writer.printf("%d,%.2f,%d", result.getRunNumber(),
                        solution.getObjectiveValue(), result.getComputationTime());

                for (String constraint : Arrays.asList("SC1", "SC2", "SC3", "SC4", "SC5", "SC6", "SC7", "SC8", "SC9",
                        "SC10")) {
                    int count = violations.getOrDefault(constraint, 0);
                    writer.printf(",%d", count);
                }

                writer.println();
            }
        }
    }

    private static void exportComparativeAnalysis(Map<String, List<ExperimentResult>> allResults, String filePath)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("Configuration1,Configuration2,MeanDiff,TStatistic,PValue,Significant");

            List<String> configs = new ArrayList<>(allResults.keySet());

            for (int i = 0; i < configs.size(); i++) {
                for (int j = i + 1; j < configs.size(); j++) {
                    String config1 = configs.get(i);
                    String config2 = configs.get(j);

                    List<Double> objectives1 = allResults.get(config1).stream()
                            .map(r -> r.getSolution().getObjectiveValue())
                            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

                    List<Double> objectives2 = allResults.get(config2).stream()
                            .map(r -> r.getSolution().getObjectiveValue())
                            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

                    if (objectives1.isEmpty() || objectives2.isEmpty())
                        continue;

                    double mean1 = objectives1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double mean2 = objectives2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double meanDiff = mean1 - mean2;

                    // Simplified t-test
                    double t = 0.0;
                    double p = 1.0;
                    boolean significant = false;

                    if (objectives1.size() > 1 && objectives2.size() > 1) {
                        // Perform basic t-test calculation
                        double var1 = objectives1.stream().mapToDouble(v -> Math.pow(v - mean1, 2)).sum()
                                / (objectives1.size() - 1);
                        double var2 = objectives2.stream().mapToDouble(v -> Math.pow(v - mean2, 2)).sum()
                                / (objectives2.size() - 1);
                        double pooledSE = Math.sqrt(var1 / objectives1.size() + var2 / objectives2.size());

                        if (pooledSE > 0) {
                            t = meanDiff / pooledSE;
                            p = Math.abs(t) > 2.0 ? 0.05 : 0.20; // Simplified p-value
                            significant = p < 0.05;
                        }
                    }

                    writer.printf("%s,%s,%.2f,%.3f,%.3f,%s\n",
                            config1, config2, meanDiff, t, p, significant);
                }
            }
        }
    }
}