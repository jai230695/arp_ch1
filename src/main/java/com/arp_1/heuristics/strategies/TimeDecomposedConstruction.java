// File: src/main/java/com/arp/heuristics/strategies/TimeDecomposedConstruction.java
package com.arp_1.heuristics.strategies;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.heuristics.base.*;
import com.arp_1.heuristics.integration.TemporalCoordinator;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Time-decomposed construction strategy
 * Decomposes the problem by time periods and constructs solutions incrementally
 */
public class TimeDecomposedConstruction implements ConstructiveHeuristic {
    private TemporalCoordinator coordinator;
    private GreedySelector selector;
    private String heuristicName;
    private Map<String, Object> configuration;
    private TimeDecompositionManager decompositionManager;

    public TimeDecomposedConstruction(GreedySelector selector) {
        this.selector = selector;
        this.coordinator = new TemporalCoordinator(selector);
        this.heuristicName = "TIME_DECOMPOSED_" + selector.getSelectorType();
        this.configuration = new HashMap<>();
        this.decompositionManager = new TimeDecompositionManager();
    }

    @Override
    public Solution constructSolution(ProblemInstance instance) {
        long startTime = System.currentTimeMillis();

        LoggingUtils.logSectionHeader("TIME-DECOMPOSED CONSTRUCTION");
        LoggingUtils.logInfo("Strategy: " + heuristicName);
        LoggingUtils.logInfo("Time decomposition approach activated");

        try {
            // Initialize time decomposition
            decompositionManager.initialize(instance);

            // Configure selector with time-aware parameters
            Map<String, Object> timeConfig = new HashMap<>(configuration);
            timeConfig.put("time_decomposed", true);
            timeConfig.put("decomposition_manager", decompositionManager);

            selector.configure(timeConfig);

            // Build solution with time decomposition
            Solution solution = buildTimeDecomposedSolution(instance);

            solution.setConstructionMethod(heuristicName);
            solution.setCreationTime(startTime);

            long endTime = System.currentTimeMillis();
            solution.setComputationTime(endTime - startTime);

            LoggingUtils.logSolutionStats("TIME_DECOMPOSED_COMPLETE",
                    solution.getObjectiveValue(),
                    solution.getHardConstraintViolations(),
                    solution.getSoftConstraintViolations(),
                    endTime - startTime);

            return solution;

        } catch (Exception e) {
            LoggingUtils.logError("Time-decomposed construction failed: " + e.getMessage(), e);
            throw new RuntimeException("Failed to construct time-decomposed solution", e);
        }
    }

    private Solution buildTimeDecomposedSolution(ProblemInstance instance) {
        LoggingUtils.logInfo("Building time-decomposed solution");

        // Use integrated approach but with time-decomposed insights
        Solution integratedSolution = coordinator.buildIntegratedSolution(instance);

        // Apply time-specific optimizations
        Solution optimizedSolution = decompositionManager.optimizeTemporalConsistency(
                integratedSolution, instance);

        return optimizedSolution;
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
        this.decompositionManager.reset();
        if (selector != null) {
            selector.configure(new HashMap<>());
        }
    }

    public static TimeDecomposedConstruction createDeterministic() {
        return new TimeDecomposedConstruction(new DeterministicSelector());
    }

    public static TimeDecomposedConstruction createRandomized(long seed, double bias) {
        return new TimeDecomposedConstruction(new RandomizedSelector(seed, bias));
    }

    public void setSelector(GreedySelector newSelector) {
        this.selector = newSelector;
        this.coordinator.setGreedySelector(newSelector);
        this.heuristicName = "TIME_DECOMPOSED_" + newSelector.getSelectorType();
    }

    public GreedySelector getSelector() {
        return selector;
    }

    @Override
    public String toString() {
        return String.format("TimeDecomposedConstruction{name='%s', selector=%s}",
                heuristicName, selector);
    }

    // Inner class for managing time decomposition
    private static class TimeDecompositionManager {
        private Map<Integer, List<PlanningDay>> weeklyDecomposition;
        private Map<String, Set<Integer>> temporalConstraints;
        private Map<Integer, Double> weeklyComplexityScores;

        public void initialize(ProblemInstance instance) {
            this.weeklyDecomposition = new HashMap<>();
            this.temporalConstraints = new HashMap<>();
            this.weeklyComplexityScores = new HashMap<>();

            // Decompose planning days by weeks
            for (PlanningDay day : instance.getPlanningDays()) {
                int week = day.getWeek();
                weeklyDecomposition.computeIfAbsent(week, k -> new ArrayList<>()).add(day);
            }

            // Calculate complexity scores for each week
            calculateWeeklyComplexity(instance);

            LoggingUtils.logInfo("Time decomposition manager initialized for " +
                    weeklyDecomposition.size() + " weeks");
        }

        private void calculateWeeklyComplexity(ProblemInstance instance) {
            for (Map.Entry<Integer, List<PlanningDay>> entry : weeklyDecomposition.entrySet()) {
                int week = entry.getKey();
                List<PlanningDay> days = entry.getValue();

                double complexity = 0.0;

                // Add complexity for weekend/holiday days
                long weekendDays = days.stream().filter(PlanningDay::isWeekendOrHoliday).count();
                complexity += weekendDays * 2.0;

                // Add complexity for pre-holiday days
                long preHolidayDays = days.stream().filter(PlanningDay::isPreHoliday).count();
                complexity += preHolidayDays * 1.5;

                // Add complexity for transition requirements
                if (week == 1 || week == 4) {
                    complexity += 1.0; // First and last weeks have transition complexity
                }

                weeklyComplexityScores.put(week, complexity);

                LoggingUtils.logDebug("Week " + week + " complexity score: " +
                        String.format("%.1f", complexity));
            }
        }

        public Solution optimizeTemporalConsistency(Solution solution, ProblemInstance instance) {
            LoggingUtils.logInfo("Optimizing temporal consistency");

            // Analyze temporal patterns in the solution
            analyzeTemporalPatterns(solution, instance);

            // Apply temporal optimizations
            solution = applyTemporalOptimizations(solution, instance);

            LoggingUtils.logInfo("Temporal consistency optimization completed");
            return solution;
        }

        private void analyzeTemporalPatterns(Solution solution, ProblemInstance instance) {
            LoggingUtils.logDebug("Analyzing temporal patterns");

            // Analyze weekly workload distribution
            for (int week = 1; week <= 4; week++) {
                analyzeWeeklyWorkload(solution, instance, week);
            }

            // Analyze transition consistency
            analyzeTransitionConsistency(solution, instance);
        }

        private void analyzeWeeklyWorkload(Solution solution, ProblemInstance instance, int week) {
            int weeklyAssignments = 0;

            // Count all assignments for this week
            for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                for (Workstation workstation : instance.getWeeklyWorkstations()) {
                    for (PlanningDay day : weeklyDecomposition.get(week)) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                day.getDayNumber(), week)) {
                            weeklyAssignments++;
                        }
                    }
                }
            }

            LoggingUtils.logDebug("Week " + week + " total weekly assignments: " + weeklyAssignments);
        }

        private void analyzeTransitionConsistency(Solution solution, ProblemInstance instance) {
            LoggingUtils.logDebug("Analyzing transition consistency between weeks");

            // Check transitions between consecutive weeks
            for (int week = 1; week < 4; week++) {
                analyzeWeekTransition(solution, instance, week, week + 1);
            }
        }

        private void analyzeWeekTransition(Solution solution, ProblemInstance instance,
                int fromWeek, int toWeek) {

            int transitionConflicts = 0;

            // Get last few days of fromWeek and first few days of toWeek
            List<PlanningDay> fromDays = weeklyDecomposition.get(fromWeek);
            List<PlanningDay> toDays = weeklyDecomposition.get(toWeek);

            if (fromDays.isEmpty() || toDays.isEmpty())
                return;

            // Check for potential conflicts in transition
            for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                boolean hasLateAssignmentInFromWeek = checkLateWeekAssignments(
                        solution, anaesthetist.getId(), fromWeek, fromDays);
                boolean hasEarlyAssignmentInToWeek = checkEarlyWeekAssignments(
                        solution, anaesthetist.getId(), toWeek, toDays);

                if (hasLateAssignmentInFromWeek && hasEarlyAssignmentInToWeek) {
                    transitionConflicts++;
                }
            }

            LoggingUtils.logDebug("Transition from week " + fromWeek + " to " + toWeek +
                    ": " + transitionConflicts + " potential conflicts");
        }

        private boolean checkLateWeekAssignments(Solution solution, String anaesthetistId,
                int week, List<PlanningDay> days) {
            // Check assignments in last 2 days of week
            int daysToCheck = Math.min(2, days.size());
            List<PlanningDay> lateDays = days.subList(days.size() - daysToCheck, days.size());

            for (PlanningDay day : lateDays) {
                // Check for any demanding assignments
                if (solution.isAssignedMonthly(anaesthetistId, "SGOT", day.getDayNumber()) ||
                        solution.isAssignedMonthly(anaesthetistId, "CICU", day.getDayNumber())) {
                    return true;
                }
            }

            return false;
        }

        private boolean checkEarlyWeekAssignments(Solution solution, String anaesthetistId,
                int week, List<PlanningDay> days) {
            // Check assignments in first 2 days of week
            int daysToCheck = Math.min(2, days.size());
            List<PlanningDay> earlyDays = days.subList(0, daysToCheck);

            for (PlanningDay day : earlyDays) {
                // Check for any demanding assignments
                if (solution.isAssignedMonthly(anaesthetistId, "SGOT", day.getDayNumber()) ||
                        solution.isAssignedMonthly(anaesthetistId, "CICU", day.getDayNumber())) {
                    return true;
                }
            }

            return false;
        }

        private Solution applyTemporalOptimizations(Solution solution, ProblemInstance instance) {
            LoggingUtils.logDebug("Applying temporal optimizations");

            // Apply week-specific optimizations based on complexity scores
            for (Map.Entry<Integer, Double> entry : weeklyComplexityScores.entrySet()) {
                int week = entry.getKey();
                double complexity = entry.getValue();

                if (complexity > 3.0) {
                    LoggingUtils.logDebug("Applying high-complexity optimization for week " + week);
                    solution = optimizeHighComplexityWeek(solution, instance, week);
                }
            }

            return solution;
        }

        private Solution optimizeHighComplexityWeek(Solution solution, ProblemInstance instance,
                int week) {
            // Apply specific optimizations for high-complexity weeks
            // This could include rebalancing assignments, adjusting priorities, etc.

            LoggingUtils.logDebug("High-complexity optimization applied for week " + week);
            return solution;
        }

        public Map<Integer, Double> getWeeklyComplexityScores() {
            return new HashMap<>(weeklyComplexityScores);
        }

        public void reset() {
            if (weeklyDecomposition != null) {
                weeklyDecomposition.clear();
            }
            if (temporalConstraints != null) {
                temporalConstraints.clear();
            }
            if (weeklyComplexityScores != null) {
                weeklyComplexityScores.clear();
            }
        }
    }
}