// File: src/main/java/com/arp/heuristics/integration/TemporalCoordinator.java
package com.arp_1.heuristics.integration;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.heuristics.base.GreedySelector;
import com.arp_1.heuristics.base.ConstructiveHeuristic;
import com.arp_1.heuristics.monthly.MonthlyRosterConstructor;
import com.arp_1.heuristics.weekly.WeeklyRosterConstructor;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Coordinates the construction of monthly and weekly rosters with temporal
 * constraints
 * FIXED: Added constructor overloads to handle different heuristic types
 */
public class TemporalCoordinator {
    private MonthlyRosterConstructor monthlyConstructor;
    private WeeklyRosterConstructor weeklyConstructor;
    private TransitionManager transitionManager;
    private IntegratedSolutionBuilder solutionBuilder;
    private GreedySelector currentSelector;

    /**
     * Constructor with GreedySelector (original)
     */
    public TemporalCoordinator(GreedySelector selector) {
        this.currentSelector = selector;
        this.monthlyConstructor = new MonthlyRosterConstructor(selector);
        this.weeklyConstructor = new WeeklyRosterConstructor(selector);
        this.transitionManager = new TransitionManager();
        this.solutionBuilder = new IntegratedSolutionBuilder();
    }

    /**
     * Constructor with ConstructiveHeuristic (ADDED FOR COMPATIBILITY)
     */
    public TemporalCoordinator(ConstructiveHeuristic heuristic) {
        // Extract selector from heuristic if possible, otherwise use default
        if (heuristic instanceof com.arp_1.heuristics.strategies.LocationAwareSequentialConstruction) {
            com.arp_1.heuristics.strategies.LocationAwareSequentialConstruction locationAware = (com.arp_1.heuristics.strategies.LocationAwareSequentialConstruction) heuristic;
            this.currentSelector = locationAware.getSelector();
        } else {
            // Default to deterministic selector
            this.currentSelector = new com.arp_1.heuristics.base.DeterministicSelector();
            LoggingUtils.logWarning("Using default DeterministicSelector for heuristic: " +
                    heuristic.getHeuristicName());
        }

        this.monthlyConstructor = new MonthlyRosterConstructor(currentSelector);
        this.weeklyConstructor = new WeeklyRosterConstructor(currentSelector);
        this.transitionManager = new TransitionManager();
        this.solutionBuilder = new IntegratedSolutionBuilder();
    }

    /**
     * Default constructor (ADDED FOR FLEXIBILITY)
     */
    public TemporalCoordinator() {
        this(new com.arp_1.heuristics.base.DeterministicSelector());
    }

    public Solution buildIntegratedSolution(ProblemInstance instance) {
        long startTime = System.currentTimeMillis();

        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("STARTING INTEGRATED SOLUTION CONSTRUCTION");
        LoggingUtils.logSeparator();
        LoggingUtils.logInfo("Anaesthetists: " + instance.getAnaesthetists().size());
        LoggingUtils.logInfo("Monthly Workstations: " + instance.getMonthlyWorkstations().size());
        LoggingUtils.logInfo("Weekly Workstations: " + instance.getWeeklyWorkstations().size());
        LoggingUtils.logInfo("Planning Days: " + instance.getPlanningDays().size());
        LoggingUtils.logInfo("Current Selector: " + currentSelector.getSelectorType());
        LoggingUtils.logSeparator();

        try {
            // Phase 1: Build monthly roster
            LoggingUtils.logInfo("PHASE 1: MONTHLY ROSTER CONSTRUCTION");
            Solution monthlyResult = monthlyConstructor.constructMonthlyRoster(instance);

            LoggingUtils.logInfo("Monthly roster construction completed");
            LoggingUtils.logInfo("Monthly objective: " + monthlyResult.getObjectiveValue());

            // Create integrated solution and add monthly assignments
            Solution finalSolution = solutionBuilder.createIntegratedSolution();
            solutionBuilder.addMonthlyAssignments(finalSolution, monthlyResult);

            // Phase 2: Build weekly rosters sequentially
            LoggingUtils.logInfo("\nPHASE 2: WEEKLY ROSTER CONSTRUCTION");

            for (int week = 1; week <= 4; week++) {
                LoggingUtils.logProgress("Weekly roster construction", week, 4);

                // Extract monthly constraints for this week
                Map<String, Set<Integer>> monthlyConstraints = extractMonthlyConstraintsForWeek(monthlyResult, week,
                        instance);

                // Handle 3-day transitions
                Map<String, List<String>> transitionConstraints = transitionManager.handle3DayTransitions(finalSolution,
                        week, instance);

                // Construct weekly roster for this week
                Solution weeklyResult = weeklyConstructor.constructWeeklyRoster(
                        instance, week, monthlyConstraints, transitionConstraints);

                // Add weekly assignments to final solution
                solutionBuilder.addWeeklyAssignments(finalSolution, weeklyResult, week);

                LoggingUtils.logInfo("Week " + week + " roster construction completed");
            }

            // Phase 3: Final integration and optimization
            LoggingUtils.logInfo("\nPHASE 3: FINAL INTEGRATION");
            finalSolution = solutionBuilder.finalizeIntegratedSolution(finalSolution, instance);

            long endTime = System.currentTimeMillis();
            finalSolution.setComputationTime(endTime - startTime);

            LoggingUtils.logSeparator();
            LoggingUtils.logInfo("INTEGRATED SOLUTION CONSTRUCTION COMPLETED");
            LoggingUtils.logSeparator();
            LoggingUtils.logInfo("Total computation time: " + (endTime - startTime) + "ms");
            LoggingUtils.logInfo("Final objective value: " + finalSolution.getObjectiveValue());
            LoggingUtils.logInfo("Hard constraint violations: " +
                    (finalSolution.hasHardConstraintViolations() ? "YES" : "NO"));
            LoggingUtils.logSeparator();

            return finalSolution;

        } catch (Exception e) {
            LoggingUtils.logError("Integrated solution construction failed", e);
            throw new RuntimeException("Failed to build integrated solution", e);
        }
    }

    private Map<String, Set<Integer>> extractMonthlyConstraintsForWeek(Solution monthlyResult,
            int week,
            ProblemInstance instance) {
        Map<String, Set<Integer>> constraints = new HashMap<>();

        // For each anaesthetist, find days in this week where they have monthly
        // assignments
        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            Set<Integer> restrictedDays = new HashSet<>();

            for (PlanningDay day : instance.getWeekDays(week)) {
                // Check if anaesthetist is assigned to any monthly workstation on this day
                boolean hasMonthlyAssignment = instance.getMonthlyWorkstations().stream()
                        .anyMatch(w -> monthlyResult.isAssignedMonthly(anaesthetist.getId(), w.getId(),
                                day.getDayNumber()));

                if (hasMonthlyAssignment) {
                    restrictedDays.add(day.getDayNumber());
                }

                // Also check for rest day restrictions
                if (monthlyResult.hasRestDayRestriction(anaesthetist.getId(), day.getDayNumber())) {
                    restrictedDays.add(day.getDayNumber());
                }
            }

            if (!restrictedDays.isEmpty()) {
                constraints.put(anaesthetist.getId(), restrictedDays);
            }
        }

        LoggingUtils.logInfo("Extracted monthly constraints for week " + week + ": " +
                constraints.size() + " anaesthetists affected");

        return constraints;
    }

    public void setGreedySelector(GreedySelector selector) {
        this.currentSelector = selector;
        this.monthlyConstructor.setGreedySelector(selector);
        this.weeklyConstructor.setGreedySelector(selector);
    }

    public GreedySelector getCurrentSelector() {
        return currentSelector;
    }
}