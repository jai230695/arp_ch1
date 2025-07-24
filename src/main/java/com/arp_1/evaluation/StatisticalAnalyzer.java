// File: src/main/java/com/arp/evaluation/StatisticalAnalyzer.java
package com.arp_1.evaluation;

import com.arp_1.main.MultipleRunsExperiment;
import java.util.*;

/**
 * Performs statistical analysis on experiment results
 */
public class StatisticalAnalyzer {

    /**
     * Analyze a single experiment result
     */
    public StatisticalReport analyzeExperiment(MultipleRunsExperiment.ExperimentResult result) {
        StatisticalReport report = new StatisticalReport();

        report.strategyName = result.getStrategyName();
        report.sampleSize = result.getNumRuns();
        report.mean = result.getMeanObjective();
        report.standardDeviation = result.getStdDevObjective();
        report.minimum = result.getBestObjective();
        report.maximum = result.getWorstObjective();

        // Calculate additional statistics
        List<Double> values = result.getObjectiveValues();
        report.median = calculateMedian(values);
        report.q1 = calculatePercentile(values, 25);
        report.q3 = calculatePercentile(values, 75);
        report.iqr = report.q3 - report.q1;

        // Calculate confidence intervals
        if (result.getNumRuns() > 1) {
            double standardError = result.getStdDevObjective() / Math.sqrt(result.getNumRuns());
            report.confidenceInterval95 = 1.96 * standardError;
            report.confidenceInterval99 = 2.576 * standardError;
        }

        // Normality and distribution analysis
        report.coefficientOfVariation = report.mean > 0 ? report.standardDeviation / report.mean : 0.0;
        report.skewness = calculateSkewness(values, report.mean, report.standardDeviation);
        report.kurtosis = calculateKurtosis(values, report.mean, report.standardDeviation);

        // Performance stability
        report.stabilityScore = calculateStabilityScore(values);

        return report;
    }

    /**
     * Compare multiple experiment results statistically
     */
    public ComparativeStatisticalReport compareExperiments(List<MultipleRunsExperiment.ExperimentResult> results) {
        ComparativeStatisticalReport report = new ComparativeStatisticalReport();

        // Individual analyses
        report.individualReports = new HashMap<>();
        for (MultipleRunsExperiment.ExperimentResult result : results) {
            StatisticalReport individual = analyzeExperiment(result);
            report.individualReports.put(result.getStrategyName(), individual);
        }

        // Overall statistics
        List<Double> allMeans = results.stream()
                .map(MultipleRunsExperiment.ExperimentResult::getMeanObjective)
                .collect(java.util.stream.Collectors.toList());

        report.overallMean = allMeans.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        report.overallStdDev = calculateStandardDeviation(allMeans, report.overallMean);

        // Pairwise statistical tests
        report.pairwiseComparisons = performPairwiseTests(results);

        // ANOVA-style analysis
        report.betweenGroupVariance = calculateBetweenGroupVariance(results);
        report.withinGroupVariance = calculateWithinGroupVariance(results);
        report.fStatistic = report.withinGroupVariance > 0 ? report.betweenGroupVariance / report.withinGroupVariance
                : 0.0;

        return report;
    }

    private double calculateMedian(List<Double> values) {
        if (values.isEmpty())
            return 0.0;

        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);

        int n = sorted.size();
        if (n % 2 == 0) {
            return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        } else {
            return sorted.get(n / 2);
        }
    }

    private double calculatePercentile(List<Double> values, double percentile) {
        if (values.isEmpty())
            return 0.0;

        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);

        double index = (percentile / 100.0) * (sorted.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sorted.get(lowerIndex);
        } else {
            double weight = index - lowerIndex;
            return sorted.get(lowerIndex) * (1 - weight) + sorted.get(upperIndex) * weight;
        }
    }

    private double calculateSkewness(List<Double> values, double mean, double stdDev) {
        if (values.size() < 2 || stdDev == 0)
            return 0.0;

        double sum = values.stream()
                .mapToDouble(x -> Math.pow((x - mean) / stdDev, 3))
                .sum();

        return sum / values.size();
    }

    private double calculateKurtosis(List<Double> values, double mean, double stdDev) {
        if (values.size() < 2 || stdDev == 0)
            return 0.0;

        double sum = values.stream()
                .mapToDouble(x -> Math.pow((x - mean) / stdDev, 4))
                .sum();

        return (sum / values.size()) - 3.0; // Excess kurtosis
    }

    private double calculateStabilityScore(List<Double> values) {
        if (values.size() < 2)
            return 1.0;

        // Stability based on coefficient of variation (lower is more stable)
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(values, mean);

        double cv = mean > 0 ? stdDev / mean : 0.0;
        return 1.0 / (1.0 + cv); // Normalized stability score (0-1, higher is better)
    }

    private double calculateStandardDeviation(List<Double> values, double mean) {
        if (values.size() < 2)
            return 0.0;

        double variance = values.stream()
                .mapToDouble(x -> Math.pow(x - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    private Map<String, Map<String, PairwiseTest>> performPairwiseTests(
            List<MultipleRunsExperiment.ExperimentResult> results) {
        Map<String, Map<String, PairwiseTest>> pairwiseTests = new HashMap<>();

        for (MultipleRunsExperiment.ExperimentResult result1 : results) {
            Map<String, PairwiseTest> comparisons = new HashMap<>();

            for (MultipleRunsExperiment.ExperimentResult result2 : results) {
                if (!result1.getStrategyName().equals(result2.getStrategyName())) {
                    PairwiseTest test = performTwoSampleTest(result1, result2);
                    comparisons.put(result2.getStrategyName(), test);
                }
            }

            pairwiseTests.put(result1.getStrategyName(), comparisons);
        }

        return pairwiseTests;
    }

    private PairwiseTest performTwoSampleTest(MultipleRunsExperiment.ExperimentResult result1,
            MultipleRunsExperiment.ExperimentResult result2) {
        PairwiseTest test = new PairwiseTest();
        test.strategy1 = result1.getStrategyName();
        test.strategy2 = result2.getStrategyName();

        // Simple two-sample test
        double mean1 = result1.getMeanObjective();
        double mean2 = result2.getMeanObjective();
        double std1 = result1.getStdDevObjective();
        double std2 = result2.getStdDevObjective();
        int n1 = result1.getNumRuns();
        int n2 = result2.getNumRuns();

        test.meanDifference = mean1 - mean2;

        if (n1 > 1 && n2 > 1) {
            // Pooled standard error
            double pooledStdError = Math.sqrt((std1 * std1 / n1) + (std2 * std2 / n2));
            test.tStatistic = pooledStdError > 0 ? test.meanDifference / pooledStdError : 0.0;

            // Simple significance test (approximation)
            test.isSignificant = Math.abs(test.tStatistic) > 1.96; // Roughly 95% confidence
            test.pValue = test.isSignificant ? 0.05 : 0.5; // Simplified
        } else {
            test.tStatistic = 0.0;
            test.isSignificant = false;
            test.pValue = 1.0;
        }

        return test;
    }

    private double calculateBetweenGroupVariance(List<MultipleRunsExperiment.ExperimentResult> results) {
        double overallMean = results.stream()
                .mapToDouble(MultipleRunsExperiment.ExperimentResult::getMeanObjective)
                .average()
                .orElse(0.0);

        double sumSquaredDeviations = results.stream()
                .mapToDouble(r -> Math.pow(r.getMeanObjective() - overallMean, 2) * r.getNumRuns())
                .sum();

        int totalSampleSize = results.stream().mapToInt(MultipleRunsExperiment.ExperimentResult::getNumRuns).sum();

        return totalSampleSize > results.size() ? sumSquaredDeviations / (results.size() - 1) : 0.0;
    }

    private double calculateWithinGroupVariance(List<MultipleRunsExperiment.ExperimentResult> results) {
        double sumSquaredDeviations = results.stream()
                .mapToDouble(r -> Math.pow(r.getStdDevObjective(), 2) * (r.getNumRuns() - 1))
                .sum();

        int totalDegreesOfFreedom = results.stream()
                .mapToInt(r -> r.getNumRuns() - 1)
                .sum();

        return totalDegreesOfFreedom > 0 ? sumSquaredDeviations / totalDegreesOfFreedom : 0.0;
    }

    /**
     * Statistical report for a single experiment
     */
    public static class StatisticalReport {
        public String strategyName;
        public int sampleSize;
        public double mean;
        public double standardDeviation;
        public double minimum;
        public double maximum;
        public double median;
        public double q1;
        public double q3;
        public double iqr;
        public double confidenceInterval95;
        public double confidenceInterval99;
        public double coefficientOfVariation;
        public double skewness;
        public double kurtosis;
        public double stabilityScore;

        @Override
        public String toString() {
            return String.format(
                    "StatisticalReport{strategy='%s', n=%d, mean=%.3f, std=%.3f, " +
                            "median=%.3f, min=%.3f, max=%.3f, cv=%.3f, stability=%.3f}",
                    strategyName, sampleSize, mean, standardDeviation, median,
                    minimum, maximum, coefficientOfVariation, stabilityScore);
        }
    }

    /**
     * Comparative statistical report
     */
    public static class ComparativeStatisticalReport {
        public Map<String, StatisticalReport> individualReports;
        public double overallMean;
        public double overallStdDev;
        public Map<String, Map<String, PairwiseTest>> pairwiseComparisons;
        public double betweenGroupVariance;
        public double withinGroupVariance;
        public double fStatistic;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ComparativeStatisticalReport{\n");
            sb.append("  Overall Mean: ").append(String.format("%.3f", overallMean)).append("\n");
            sb.append("  Overall StdDev: ").append(String.format("%.3f", overallStdDev)).append("\n");
            sb.append("  F-Statistic: ").append(String.format("%.3f", fStatistic)).append("\n");
            sb.append("  Individual Reports:\n");
            for (StatisticalReport report : individualReports.values()) {
                sb.append("    ").append(report).append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Perform t-test between two groups of values
     */
    public boolean performTTest(List<Double> group1, List<Double> group2, double significanceLevel) {
        if (group1.size() < 2 || group2.size() < 2) {
            return false; // Insufficient data for t-test
        }

        // Filter out invalid values (MAX_VALUE indicates failed runs)
        List<Double> validGroup1 = group1.stream()
                .filter(val -> val < Double.MAX_VALUE)
                .collect(java.util.stream.Collectors.toList());

        List<Double> validGroup2 = group2.stream()
                .filter(val -> val < Double.MAX_VALUE)
                .collect(java.util.stream.Collectors.toList());

        if (validGroup1.size() < 2 || validGroup2.size() < 2) {
            return false;
        }

        // Calculate means
        double mean1 = validGroup1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double mean2 = validGroup2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // Calculate standard deviations
        double std1 = calculateStandardDeviation(validGroup1, mean1);
        double std2 = calculateStandardDeviation(validGroup2, mean2);

        // Calculate pooled standard error
        int n1 = validGroup1.size();
        int n2 = validGroup2.size();

        double pooledStdError = Math.sqrt((std1 * std1 / n1) + (std2 * std2 / n2));

        if (pooledStdError == 0.0) {
            return Math.abs(mean1 - mean2) > 0; // If no variance, any difference is significant
        }

        // Calculate t-statistic
        double tStatistic = Math.abs(mean1 - mean2) / pooledStdError;

        // Degrees of freedom (Welch's t-test approximation)
        double df = Math.pow(std1 * std1 / n1 + std2 * std2 / n2, 2) /
                (Math.pow(std1 * std1 / n1, 2) / (n1 - 1) + Math.pow(std2 * std2 / n2, 2) / (n2 - 1));

        // Critical value for two-tailed test at given significance level
        // Simplified: using normal approximation for large samples, t-table lookup for
        // small samples
        double criticalValue;
        if (df >= 30) {
            // Normal approximation
            criticalValue = significanceLevel <= 0.01 ? 2.576 : significanceLevel <= 0.05 ? 1.96 : 1.645;
        } else {
            // Simplified t-table lookup
            criticalValue = significanceLevel <= 0.01 ? 3.0 : significanceLevel <= 0.05 ? 2.0 : 1.5;
        }

        return tStatistic > criticalValue;
    }

    /**
     * Calculate effect size (Cohen's d) between two groups
     */
    public double calculateEffectSize(List<Double> group1, List<Double> group2) {
        if (group1.size() < 2 || group2.size() < 2) {
            return 0.0;
        }

        // Filter valid values
        List<Double> validGroup1 = group1.stream()
                .filter(val -> val < Double.MAX_VALUE)
                .collect(java.util.stream.Collectors.toList());

        List<Double> validGroup2 = group2.stream()
                .filter(val -> val < Double.MAX_VALUE)
                .collect(java.util.stream.Collectors.toList());

        if (validGroup1.isEmpty() || validGroup2.isEmpty()) {
            return 0.0;
        }

        double mean1 = validGroup1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double mean2 = validGroup2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double std1 = calculateStandardDeviation(validGroup1, mean1);
        double std2 = calculateStandardDeviation(validGroup2, mean2);

        // Pooled standard deviation
        double pooledStd = Math.sqrt(((validGroup1.size() - 1) * std1 * std1 +
                (validGroup2.size() - 1) * std2 * std2) /
                (validGroup1.size() + validGroup2.size() - 2));

        if (pooledStd == 0.0) {
            return 0.0;
        }

        return Math.abs(mean1 - mean2) / pooledStd;
    }

    /**
     * Perform power analysis to determine if sample size is adequate
     */
    public PowerAnalysisResult performPowerAnalysis(MultipleRunsExperiment.ExperimentResult result,
            double effectSize, double significanceLevel) {
        PowerAnalysisResult analysis = new PowerAnalysisResult();
        analysis.sampleSize = result.getNumRuns();
        analysis.effectSize = effectSize;
        analysis.significanceLevel = significanceLevel;

        // Simplified power calculation
        // Power increases with sample size and effect size
        double power = 1.0 - Math.exp(-0.1 * analysis.sampleSize * effectSize * effectSize);
        power = Math.min(0.99, Math.max(0.05, power)); // Bound between 5% and 99%

        analysis.statisticalPower = power;
        analysis.isAdequate = power >= 0.8; // 80% power is typically considered adequate

        // Recommended sample size for 80% power
        if (!analysis.isAdequate && effectSize > 0) {
            analysis.recommendedSampleSize = (int) Math.ceil(80.0 / (effectSize * effectSize * 10));
        } else {
            analysis.recommendedSampleSize = analysis.sampleSize;
        }

        return analysis;
    }

    /**
     * Power analysis result
     */
    public static class PowerAnalysisResult {
        public int sampleSize;
        public double effectSize;
        public double significanceLevel;
        public double statisticalPower;
        public boolean isAdequate;
        public int recommendedSampleSize;

        @Override
        public String toString() {
            return String.format("PowerAnalysis{n=%d, effect=%.3f, power=%.3f, adequate=%s, recommended=%d}",
                    sampleSize, effectSize, statisticalPower, isAdequate, recommendedSampleSize);
        }
    }

    /**
     * Pairwise statistical test result
     */
    public static class PairwiseTest {
        public String strategy1;
        public String strategy2;
        public double meanDifference;
        public double tStatistic;
        public double pValue;
        public boolean isSignificant;

        @Override
        public String toString() {
            return String.format("PairwiseTest{%s vs %s: diff=%.3f, t=%.3f, p=%.3f, sig=%s}",
                    strategy1, strategy2, meanDifference, tStatistic, pValue, isSignificant);
        }
    }
}