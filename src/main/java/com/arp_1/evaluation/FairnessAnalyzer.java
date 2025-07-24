// File: src/main/java/com/arp/evaluation/FairnessAnalyzer.java
package com.arp_1.evaluation;

import com.arp_1.core.models.*;
import com.arp_1.utils.LoggingUtils;
import com.arp_1.core.data.ProblemInstance;
import java.util.*;

/**
 * Analyzes fairness aspects of roster solutions
 */
public class FairnessAnalyzer {

    /**
     * Comprehensive fairness analysis
     */
    public FairnessReport analyzeFairness(Solution solution, ProblemInstance instance) {
        FairnessReport report = new FairnessReport();

        // Overall workload distribution
        report.workloadDistribution = analyzeWorkloadDistribution(solution, instance);

        // Weekend distribution fairness
        report.weekendDistribution = analyzeWeekendDistribution(solution, instance);

        // Pre-holiday distribution fairness
        report.preHolidayDistribution = analyzePreHolidayDistribution(solution, instance);

        // Workstation-specific fairness
        report.workstationFairness = analyzeWorkstationFairness(solution, instance);

        // Calculate overall fairness score
        report.overallFairnessScore = calculateOverallFairnessScore(report);

        return report;
    }

    private DistributionAnalysis analyzeWorkloadDistribution(Solution solution, ProblemInstance instance) {
        Map<String, Integer> workloadCounts = new HashMap<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int totalWorkload = 0;

            // Count monthly assignments
            for (Workstation workstation : instance.getMonthlyWorkstations()) {
                totalWorkload += solution.countMonthlyAssignments(anaesthetist.getId(), workstation.getId());
            }

            // Count weekly assignments
            for (int week = 1; week <= 4; week++) {
                for (Workstation workstation : instance.getWeeklyWorkstations()) {
                    totalWorkload += solution.countWeeklyAssignments(anaesthetist.getId(), workstation.getId(), week);
                }
            }

            workloadCounts.put(anaesthetist.getId(), totalWorkload);
        }

        return analyzeDistribution(workloadCounts, "Overall Workload");
    }

    private DistributionAnalysis analyzeWeekendDistribution(Solution solution, ProblemInstance instance) {
        Map<String, Integer> weekendCounts = new HashMap<>();
        Set<Integer> weekendDays = instance.getWeekendHolidays();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int weekendAssignments = 0;

            for (Integer day : weekendDays) {
                // Check monthly assignments on weekend days
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    if (solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), day)) {
                        weekendAssignments++;
                        break; // Count only once per day
                    }
                }

                // Check weekly assignments on weekend days
                PlanningDay planningDay = instance.getPlanningDay(day);
                if (planningDay != null) {
                    int week = planningDay.getWeek();
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(), day, week)) {
                            weekendAssignments++;
                            break; // Count only once per day
                        }
                    }
                }
            }

            weekendCounts.put(anaesthetist.getId(), weekendAssignments);
        }

        return analyzeDistribution(weekendCounts, "Weekend Distribution");
    }

    private DistributionAnalysis analyzePreHolidayDistribution(Solution solution, ProblemInstance instance) {
        Map<String, Integer> preHolidayCounts = new HashMap<>();
        Set<Integer> preHolidayDays = instance.getPreHolidays();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int preHolidayAssignments = 0;

            for (Integer day : preHolidayDays) {
                // Check monthly assignments on pre-holiday days
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    if (solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), day)) {
                        preHolidayAssignments++;
                        break;
                    }
                }

                // Check weekly assignments on pre-holiday days
                PlanningDay planningDay = instance.getPlanningDay(day);
                if (planningDay != null) {
                    int week = planningDay.getWeek();
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(), day, week)) {
                            preHolidayAssignments++;
                            break;
                        }
                    }
                }
            }

            preHolidayCounts.put(anaesthetist.getId(), preHolidayAssignments);
        }

        return analyzeDistribution(preHolidayCounts, "Pre-Holiday Distribution");
    }

    private Map<String, DistributionAnalysis> analyzeWorkstationFairness(Solution solution, ProblemInstance instance) {
        Map<String, DistributionAnalysis> workstationFairness = new HashMap<>();

        // Analyze each workstation separately
        for (Workstation workstation : instance.getAllWorkstations()) {
            Map<String, Integer> assignmentCounts = new HashMap<>();

            for (Anaesthetist anaesthetist : instance.getQualifiedAnaesthetists(workstation.getId())) {
                int assignments = 0;

                if (workstation.isMonthlyRoster()) {
                    assignments = solution.countMonthlyAssignments(anaesthetist.getId(), workstation.getId());
                } else {
                    for (int week = 1; week <= 4; week++) {
                        assignments += solution.countWeeklyAssignments(anaesthetist.getId(), workstation.getId(), week);
                    }
                }

                assignmentCounts.put(anaesthetist.getId(), assignments);
            }

            DistributionAnalysis analysis = analyzeDistribution(assignmentCounts, workstation.getId());
            workstationFairness.put(workstation.getId(), analysis);
        }

        return workstationFairness;
    }

    private DistributionAnalysis analyzeDistribution(Map<String, Integer> counts, String distributionName) {
        if (counts.isEmpty()) {
            return new DistributionAnalysis(distributionName, 0, 0, 0, 0.0, 0.0, 1.0);
        }

        Collection<Integer> values = counts.values();
        int min = Collections.min(values);
        int max = Collections.max(values);
        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0.0);

        double variance = values.stream()
                .mapToDouble(val -> Math.pow(val - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        // Fairness score (higher is better, based on coefficient of variation)
        double fairnessScore = mean > 0 ? 1.0 / (1.0 + (stdDev / mean)) : 1.0;

        return new DistributionAnalysis(distributionName, min, max, max - min, mean, stdDev, fairnessScore);
    }

    private double calculateOverallFairnessScore(FairnessReport report) {
        // Weighted average of different fairness components
        double workloadWeight = 0.4;
        double weekendWeight = 0.3;
        double preHolidayWeight = 0.1;
        double workstationWeight = 0.2;

        double workstationAverage = report.workstationFairness.values().stream()
                .mapToDouble(analysis -> analysis.fairnessScore)
                .average()
                .orElse(0.0);

        return workloadWeight * report.workloadDistribution.fairnessScore +
                weekendWeight * report.weekendDistribution.fairnessScore +
                preHolidayWeight * report.preHolidayDistribution.fairnessScore +
                workstationWeight * workstationAverage;
    }

    /**
     * Analyze a single solution for fairness
     */
    public void analyzeSolution(Solution solution, ProblemInstance instance) {
        FairnessReport report = analyzeFairness(solution, instance);

        // Log detailed fairness analysis
        LoggingUtils.logInfo("Fairness Analysis Results:");
        LoggingUtils.logInfo("  Overall Fairness Score: " + String.format("%.3f", report.overallFairnessScore));
        LoggingUtils.logInfo("  " + report.workloadDistribution.toString());
        LoggingUtils.logInfo("  " + report.weekendDistribution.toString());
        LoggingUtils.logInfo("  " + report.preHolidayDistribution.toString());

        // Log workstation-specific fairness
        LoggingUtils.logInfo("  Workstation Fairness:");
        for (Map.Entry<String, DistributionAnalysis> entry : report.workstationFairness.entrySet()) {
            LoggingUtils.logInfo("    " + entry.getValue().toString());
        }
    }

    /**
     * Calculate fairness metrics for a solution
     */
    public FairnessMetrics calculateFairnessMetrics(Solution solution, ProblemInstance instance) {
        FairnessReport report = analyzeFairness(solution, instance);

        // Calculate individual fairness components
        double workloadFairness = report.workloadDistribution.fairnessScore;
        double weekendFairness = report.weekendDistribution.fairnessScore;
        double preHolidayFairness = report.preHolidayDistribution.fairnessScore;

        // Calculate workstation-specific fairness average
        double workstationFairness = report.workstationFairness.values().stream()
                .mapToDouble(analysis -> analysis.fairnessScore)
                .average()
                .orElse(0.0);

        // Overall fairness is already calculated in the report
        double overallFairness = report.overallFairnessScore;

        return new FairnessMetrics(workloadFairness, weekendFairness, preHolidayFairness, overallFairness);
    }

    /**
     * Calculate fairness score for specific workstation
     */
    public double calculateWorkstationFairness(Solution solution, ProblemInstance instance, String workstationId) {
        Workstation workstation = instance.getWorkstationById(workstationId);
        if (workstation == null) {
            return 0.0;
        }

        Map<String, Integer> assignmentCounts = new HashMap<>();

        for (Anaesthetist anaesthetist : instance.getQualifiedAnaesthetists(workstationId)) {
            int assignments = 0;

            if (workstation.isMonthlyRoster()) {
                assignments = solution.countMonthlyAssignments(anaesthetist.getId(), workstationId);
            } else {
                for (int week = 1; week <= 4; week++) {
                    assignments += solution.countWeeklyAssignments(anaesthetist.getId(), workstationId, week);
                }
            }

            assignmentCounts.put(anaesthetist.getId(), assignments);
        }

        DistributionAnalysis analysis = analyzeDistribution(assignmentCounts, workstationId);
        return analysis.fairnessScore;
    }

    /**
     * Compare fairness between two solutions
     */
    public FairnessComparison compareFairness(Solution solution1, Solution solution2, ProblemInstance instance) {
        FairnessMetrics metrics1 = calculateFairnessMetrics(solution1, instance);
        FairnessMetrics metrics2 = calculateFairnessMetrics(solution2, instance);

        return new FairnessComparison(metrics1, metrics2);
    }

    /**
     * Get fairness improvement suggestions
     */
    public List<FairnessImprovement> getFairnessImprovements(Solution solution, ProblemInstance instance) {
        List<FairnessImprovement> improvements = new ArrayList<>();
        FairnessReport report = analyzeFairness(solution, instance);

        // Check for workload imbalances
        if (report.workloadDistribution.range > 3) {
            improvements.add(new FairnessImprovement(
                    "WORKLOAD_BALANCE",
                    "Large workload range detected (" + report.workloadDistribution.range +
                            "). Consider redistributing assignments.",
                    report.workloadDistribution.range * 0.1 // Priority based on severity
            ));
        }

        // Check for weekend imbalances
        if (report.weekendDistribution.range > 2) {
            improvements.add(new FairnessImprovement(
                    "WEEKEND_BALANCE",
                    "Weekend assignment imbalance detected (" + report.weekendDistribution.range +
                            "). Consider rotating weekend duties.",
                    report.weekendDistribution.range * 0.15));
        }

        // Check workstation-specific imbalances
        for (Map.Entry<String, DistributionAnalysis> entry : report.workstationFairness.entrySet()) {
            DistributionAnalysis analysis = entry.getValue();
            if (analysis.fairnessScore < 0.7 && analysis.range > 1) {
                improvements.add(new FairnessImprovement(
                        "WORKSTATION_BALANCE",
                        "Imbalance in " + entry.getKey() + " assignments (fairness: " +
                                String.format("%.3f", analysis.fairnessScore) + "). Consider reassignment.",
                        (1.0 - analysis.fairnessScore) * 0.2));
            }
        }

        // Sort by priority (highest first)
        improvements.sort((a, b) -> Double.compare(b.getPriority(), a.getPriority()));

        return improvements;
    }

    /**
     * Generate fairness report summary
     */
    public String generateFairnessReportSummary(Solution solution, ProblemInstance instance) {
        FairnessReport report = analyzeFairness(solution, instance);
        FairnessMetrics metrics = calculateFairnessMetrics(solution, instance);

        StringBuilder summary = new StringBuilder();
        summary.append("=== FAIRNESS ANALYSIS SUMMARY ===\n");
        summary.append(String.format("Overall Fairness Score: %.3f\n", metrics.getOverallFairness()));
        summary.append(String.format("Workload Fairness: %.3f (range: %d)\n",
                metrics.getWorkloadFairness(), report.workloadDistribution.range));
        summary.append(String.format("Weekend Fairness: %.3f (range: %d)\n",
                metrics.getWeekendFairness(), report.weekendDistribution.range));
        summary.append(String.format("Pre-Holiday Fairness: %.3f (range: %d)\n",
                metrics.getPreHolidayFairness(), report.preHolidayDistribution.range));

        summary.append("\nWorkstation Fairness Breakdown:\n");
        for (Map.Entry<String, DistributionAnalysis> entry : report.workstationFairness.entrySet()) {
            DistributionAnalysis analysis = entry.getValue();
            summary.append(String.format("  %-10s: %.3f (range: %d, mean: %.1f)\n",
                    entry.getKey(), analysis.fairnessScore, analysis.range, analysis.mean));
        }

        // Add improvement suggestions
        List<FairnessImprovement> improvements = getFairnessImprovements(solution, instance);
        if (!improvements.isEmpty()) {
            summary.append("\nImprovement Suggestions:\n");
            for (int i = 0; i < Math.min(3, improvements.size()); i++) {
                FairnessImprovement improvement = improvements.get(i);
                summary.append(String.format("  %d. %s (Priority: %.2f)\n",
                        i + 1, improvement.getDescription(), improvement.getPriority()));
            }
        }

        summary.append("================================");
        return summary.toString();
    }

    /**
     * Distribution analysis data structure
     */
    public static class DistributionAnalysis {
        public final String name;
        public final int min;
        public final int max;
        public final int range;
        public final double mean;
        public final double stdDev;
        public final double fairnessScore; // 0-1, higher is better

        public DistributionAnalysis(String name, int min, int max, int range,
                double mean, double stdDev, double fairnessScore) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.range = range;
            this.mean = mean;
            this.stdDev = stdDev;
            this.fairnessScore = fairnessScore;
        }

        @Override
        public String toString() {
            return String.format("%s: min=%d, max=%d, range=%d, mean=%.2f, stdDev=%.2f, fairness=%.3f",
                    name, min, max, range, mean, stdDev, fairnessScore);
        }
    }

    /**
     * Comprehensive fairness report
     */
    public static class FairnessReport {
        public DistributionAnalysis workloadDistribution;
        public DistributionAnalysis weekendDistribution;
        public DistributionAnalysis preHolidayDistribution;
        public Map<String, DistributionAnalysis> workstationFairness;
        public double overallFairnessScore;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("FairnessReport{\n");
            sb.append("  Overall Score: ").append(String.format("%.3f", overallFairnessScore)).append("\n");
            sb.append("  ").append(workloadDistribution).append("\n");
            sb.append("  ").append(weekendDistribution).append("\n");
            sb.append("  ").append(preHolidayDistribution).append("\n");
            sb.append("  Workstation Fairness:\n");
            for (Map.Entry<String, DistributionAnalysis> entry : workstationFairness.entrySet()) {
                sb.append("    ").append(entry.getValue()).append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Fairness metrics for simple comparison
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

    /**
     * Fairness comparison between two solutions
     */
    public static class FairnessComparison {
        private FairnessMetrics solution1Metrics;
        private FairnessMetrics solution2Metrics;
        private double workloadImprovement;
        private double weekendImprovement;
        private double overallImprovement;

        public FairnessComparison(FairnessMetrics metrics1, FairnessMetrics metrics2) {
            this.solution1Metrics = metrics1;
            this.solution2Metrics = metrics2;
            this.workloadImprovement = metrics2.getWorkloadFairness() - metrics1.getWorkloadFairness();
            this.weekendImprovement = metrics2.getWeekendFairness() - metrics1.getWeekendFairness();
            this.overallImprovement = metrics2.getOverallFairness() - metrics1.getOverallFairness();
        }

        public boolean isSolution2Better() {
            return overallImprovement > 0;
        }

        public double getOverallImprovement() {
            return overallImprovement;
        }

        public double getWorkloadImprovement() {
            return workloadImprovement;
        }

        public double getWeekendImprovement() {
            return weekendImprovement;
        }

        @Override
        public String toString() {
            return String.format("FairnessComparison{overall: %+.3f, workload: %+.3f, weekend: %+.3f}",
                    overallImprovement, workloadImprovement, weekendImprovement);
        }
    }

    /**
     * Fairness improvement suggestion
     */
    public static class FairnessImprovement {
        private String type;
        private String description;
        private double priority;

        public FairnessImprovement(String type, String description, double priority) {
            this.type = type;
            this.description = description;
            this.priority = priority;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public double getPriority() {
            return priority;
        }

        @Override
        public String toString() {
            return String.format("FairnessImprovement{type='%s', priority=%.2f, desc='%s'}",
                    type, priority, description);
        }
    }
}
