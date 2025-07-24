// File: src/main/java/com/arp/heuristics/weekly/MultiLocationConstructor.java
package com.arp_1.heuristics.weekly;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.heuristics.base.GreedySelector;
import com.arp_1.heuristics.base.PriorityCalculator;
import com.arp_1.utils.LoggingUtils;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles multi-location weekly assignments with exclusivity rules
 */
public class MultiLocationConstructor {
    private GreedySelector selector;
    private PriorityCalculator priorityCalculator;

    // Exclusive workstation groups (HC10)
    private static final String[] EXCLUSIVE_WORKSTATIONS = { "MCT", "MWK", "EWK" };
    private static final Map<String, Set<String>> EXCLUSIVITY_RULES = Map.of(
            "MCT", Set.of("MMAU", "MMIU", "EU1", "EU2", "OHMAU", "OHMIU"),
            "MWK", Set.of("MMAU", "MMIU", "EU1", "EU2", "OHMAU", "OHMIU", "MCT"),
            "EWK", Set.of("EU1", "EU2"));

    public MultiLocationConstructor(GreedySelector selector) {
        this.selector = selector;
        this.priorityCalculator = new PriorityCalculator();
    }

    public Solution constructExclusiveAssignments(ProblemInstance instance, int week,
            Solution solution,
            Map<String, Set<Integer>> monthlyConstraints) {

        LoggingUtils.logInfo("Constructing exclusive weekly assignments for week " + week);

        // Process exclusive workstations in priority order
        List<String> prioritizedExclusives = Arrays.asList("MCT", "MWK", "EWK");

        for (String workstationId : prioritizedExclusives) {
            Workstation workstation = instance.getWorkstationById(workstationId);
            if (workstation != null && workstation.isWeeklyRoster()) {
                assignExclusiveWorkstation(workstation, instance, week, solution, monthlyConstraints);
            }
        }

        return solution;
    }

    private void assignExclusiveWorkstation(Workstation workstation, ProblemInstance instance,
            int week, Solution solution,
            Map<String, Set<Integer>> monthlyConstraints) {

        LoggingUtils.logInfo("Assigning exclusive workstation: " + workstation.getId());

        for (PlanningDay day : instance.getWeekDays(week)) {
            int required = instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());

            if (required > 0) {
                assignExclusiveWorkstationDay(workstation, day, required, instance,
                        week, solution, monthlyConstraints);
            }
        }
    }

    private void assignExclusiveWorkstationDay(Workstation workstation, PlanningDay day,
            int required, ProblemInstance instance,
            int week, Solution solution,
            Map<String, Set<Integer>> monthlyConstraints) {

        // Get eligible candidates
        List<String> candidates = getEligibleExclusiveCandidates(workstation, day, instance,
                week, solution, monthlyConstraints);

        if (candidates.size() < required) {
            LoggingUtils.logWarning("Insufficient candidates for " + workstation.getId() +
                    " on day " + day.getDayNumber() + " (week " + week + "): need " +
                    required + ", have " + candidates.size());
            required = Math.min(required, candidates.size());
        }

        if (candidates.isEmpty()) {
            LoggingUtils.logWarning("No eligible candidates for " + workstation.getId() +
                    " on day " + day.getDayNumber() + " (week " + week + ")");
            return;
        }

        // Calculate priorities
        Map<String, Double> priorities = new HashMap<>();
        for (String candidateId : candidates) {
            double priority = calculateExclusivePriority(candidateId, workstation, day,
                    instance, week, solution);
            priorities.put(candidateId, priority);
        }

        // Select anaesthetists
        List<String> selectedAnaesthetists = selector.selectAnaesthetists(candidates, priorities, required);

        // Make assignments
        for (String anaesthetistId : selectedAnaesthetists) {
            solution.assignWeekly(anaesthetistId, workstation.getId(), day.getDayNumber(), week);

            LoggingUtils.logAssignment(workstation.getId(), day.getDayNumber(),
                    Arrays.asList(anaesthetistId), "WEEKLY_" + selector.getSelectorType());
        }
    }

    private List<String> getEligibleExclusiveCandidates(Workstation workstation, PlanningDay day,
            ProblemInstance instance, int week,
            Solution solution,
            Map<String, Set<Integer>> monthlyConstraints) {

        return instance.getAnaesthetists().stream()
                .filter(a -> a.isActive())
                .filter(a -> a.isQualifiedFor(workstation.getId()))
                .filter(a -> !instance.isAnaesthetistUnavailable(a.getId(), day.getDayNumber()))
                .filter(a -> !hasMonthlyConflict(a.getId(), day.getDayNumber(), monthlyConstraints))
                .filter(a -> !violatesExclusivityRules(a.getId(), workstation.getId(), day, week, solution))
                .map(Anaesthetist::getId)
                .collect(Collectors.toList());
    }

    private boolean hasMonthlyConflict(String anaesthetistId, int dayNumber,
            Map<String, Set<Integer>> monthlyConstraints) {
        Set<Integer> restrictedDays = monthlyConstraints.get(anaesthetistId);
        return restrictedDays != null && restrictedDays.contains(dayNumber);
    }

    private boolean violatesExclusivityRules(String anaesthetistId, String workstationId,
            PlanningDay day, int week, Solution solution) {

        // Check if already assigned to any conflicting weekly workstation
        Set<String> conflictingWorkstations = EXCLUSIVITY_RULES.get(workstationId);
        if (conflictingWorkstations != null) {
            for (String conflictingId : conflictingWorkstations) {
                if (solution.isAssignedWeekly(anaesthetistId, conflictingId, day.getDayNumber(), week)) {
                    return true;
                }
            }
        }

        // Check OHMAU/OHMIU exclusivity
        if ("OHMAU".equals(workstationId) &&
                solution.isAssignedWeekly(anaesthetistId, "OHMIU", day.getDayNumber(), week)) {
            return true;
        }

        if ("OHMIU".equals(workstationId) &&
                solution.isAssignedWeekly(anaesthetistId, "OHMAU", day.getDayNumber(), week)) {
            return true;
        }

        return false;
    }

    private double calculateExclusivePriority(String anaesthetistId, Workstation workstation,
            PlanningDay day, ProblemInstance instance,
            int week, Solution solution) {

        // Base priority
        double priority = priorityCalculator.calculatePriority(anaesthetistId, workstation,
                day, instance, solution);

        Anaesthetist anaesthetist = instance.getAnaesthetistById(anaesthetistId);

        // Exclusive workstation specific bonuses
        switch (workstation.getId()) {
            case "MCT":
                // Cardiothoracic specialist bonus
                if (anaesthetist.hasPreferenceFor("CCT") || anaesthetist.hasPreferenceFor("SCT")) {
                    priority += 30.0;
                }
                // Check for cardiothoracic request
                if (instance.getRequest(anaesthetistId, day.getDayNumber())
                        .map(r -> "CT".equals(r.getRequestType())).orElse(false)) {
                    priority += 50.0;
                }
                break;

            case "MWK":
                // General morning work preference
                if (instance.hasMorningShiftRequest(anaesthetistId, day.getDayNumber())) {
                    priority += 25.0;
                }
                break;

            case "EWK":
                // Evening work preference
                if (instance.hasEveningShiftRequest(anaesthetistId, day.getDayNumber())) {
                    priority += 25.0;
                }
                // Should not conflict with EU1, EU2
                boolean hasEveningConflict = solution.isAssignedWeekly(anaesthetistId, "EU1", day.getDayNumber(), week)
                        ||
                        solution.isAssignedWeekly(anaesthetistId, "EU2", day.getDayNumber(), week);
                if (hasEveningConflict) {
                    priority -= 100.0; // Strong penalty
                }
                break;
        }

        // Workload balance bonus
        int currentWeeklyAssignments = countWeeklyAssignments(anaesthetistId, week, solution);
        if (currentWeeklyAssignments < 3) { // Target 2-3 weekly assignments per anaesthetist
            priority += (3 - currentWeeklyAssignments) * 10.0;
        }

        return priority;
    }

    private int countWeeklyAssignments(String anaesthetistId, int week, Solution solution) {
        int count = 0;
        Map<String, Map<String, Map<Integer, Boolean>>> weekData = solution.getWeeklyAssignments(week);
        Map<String, Map<Integer, Boolean>> anaesthetistData = weekData.get(anaesthetistId);

        if (anaesthetistData != null) {
            for (Map<Integer, Boolean> workstationAssignments : anaesthetistData.values()) {
                count += (int) workstationAssignments.values().stream().mapToLong(assigned -> assigned ? 1 : 0).sum();
            }
        }

        return count;
    }
}
