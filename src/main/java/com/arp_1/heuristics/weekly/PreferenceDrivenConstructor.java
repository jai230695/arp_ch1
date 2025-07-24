// File: src/main/java/com/arp/heuristics/weekly/PreferenceDrivenConstructor.java
package com.arp_1.heuristics.weekly;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.heuristics.base.GreedySelector;
import com.arp_1.heuristics.base.PriorityCalculator;
import com.arp_1.utils.LoggingUtils;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Completes weekly roster with focus on anaesthetist preferences
 */
public class PreferenceDrivenConstructor {
    private GreedySelector selector;
    private PriorityCalculator priorityCalculator;

    public PreferenceDrivenConstructor(GreedySelector selector) {
        this.selector = selector;
        this.priorityCalculator = new PriorityCalculator();
    }

    public Solution completeWeeklyRoster(ProblemInstance instance, int week, Solution partialSolution,
            Map<String, Set<Integer>> monthlyConstraints) {

        LoggingUtils.logInfo("Completing weekly roster for week " + week + " with preference focus");

        // Get remaining workstations (excluding those handled by
        // MultiLocationConstructor)
        List<Workstation> remainingWorkstations = instance.getWeeklyWorkstations().stream()
                .filter(w -> !isExclusiveWorkstation(w.getId()))
                .collect(Collectors.toList());

        // Process workstations in preference priority order
        List<Workstation> prioritizedWorkstations = prioritizeByPreferenceImportance(remainingWorkstations);

        for (Workstation workstation : prioritizedWorkstations) {
            LoggingUtils.logInfo("Processing weekly workstation: " + workstation.getId());
            assignWorkstationWithPreferences(workstation, instance, week, partialSolution, monthlyConstraints);
        }

        LoggingUtils.logInfo("Weekly roster completion for week " + week + " finished");
        return partialSolution;
    }

    private boolean isExclusiveWorkstation(String workstationId) {
        return Arrays.asList("MCT", "MWK", "EWK").contains(workstationId);
    }

    private List<Workstation> prioritizeByPreferenceImportance(List<Workstation> workstations) {
        return workstations.stream()
                .sorted((w1, w2) -> {
                    // Priority: Morning shifts > Evening shifts > Office hours
                    int priority1 = getPreferencePriority(w1);
                    int priority2 = getPreferencePriority(w2);
                    return Integer.compare(priority1, priority2);
                })
                .collect(Collectors.toList());
    }

    private int getPreferencePriority(Workstation workstation) {
        if (workstation.isMorningShift()) {
            return 1; // Highest priority (morning preferences common)
        } else if (workstation.isEveningShift()) {
            return 2; // Second priority
        } else if (workstation.isOfficeHoursShift()) {
            return 3; // Lower priority
        } else {
            return 4; // Lowest priority
        }
    }

    private void assignWorkstationWithPreferences(Workstation workstation, ProblemInstance instance,
            int week, Solution solution,
            Map<String, Set<Integer>> monthlyConstraints) {

        for (PlanningDay day : instance.getWeekDays(week)) {
            int required = instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());

            if (required > 0) {
                assignWorkstationDayWithPreferences(workstation, day, required, instance,
                        week, solution, monthlyConstraints);
            }
        }
    }

    private void assignWorkstationDayWithPreferences(Workstation workstation, PlanningDay day,
            int required, ProblemInstance instance,
            int week, Solution solution,
            Map<String, Set<Integer>> monthlyConstraints) {

        // Get eligible candidates
        List<String> candidates = getEligiblePreferenceCandidates(workstation, day, instance,
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

        // Calculate preference-weighted priorities
        Map<String, Double> priorities = calculatePreferencePriorities(candidates, workstation,
                day, instance, week, solution);

        // Select anaesthetists
        List<String> selectedAnaesthetists = selector.selectAnaesthetists(candidates, priorities, required);

        // Make assignments
        for (String anaesthetistId : selectedAnaesthetists) {
            solution.assignWeekly(anaesthetistId, workstation.getId(), day.getDayNumber(), week);

            LoggingUtils.logAssignment(workstation.getId(), day.getDayNumber(),
                    Arrays.asList(anaesthetistId), "WEEKLY_PREF_" + selector.getSelectorType());
        }
    }

    private List<String> getEligiblePreferenceCandidates(Workstation workstation, PlanningDay day,
            ProblemInstance instance, int week,
            Solution solution,
            Map<String, Set<Integer>> monthlyConstraints) {

        return instance.getAnaesthetists().stream()
                .filter(a -> a.isActive())
                .filter(a -> a.isQualifiedFor(workstation.getId()))
                .filter(a -> !instance.isAnaesthetistUnavailable(a.getId(), day.getDayNumber()))
                .filter(a -> !hasMonthlyConflict(a.getId(), day.getDayNumber(), monthlyConstraints))
                .filter(a -> !violatesWeeklyConstraints(a.getId(), workstation, day, week, solution, instance))
                .filter(a -> !isAlreadyFullyAssigned(a.getId(), day, week, solution, instance))
                .map(Anaesthetist::getId)
                .collect(Collectors.toList());
    }

    private boolean hasMonthlyConflict(String anaesthetistId, int dayNumber,
            Map<String, Set<Integer>> monthlyConstraints) {
        Set<Integer> restrictedDays = monthlyConstraints.get(anaesthetistId);
        return restrictedDays != null && restrictedDays.contains(dayNumber);
    }

    private boolean violatesWeeklyConstraints(String anaesthetistId, Workstation workstation,
            PlanningDay day, int week, Solution solution,
            ProblemInstance instance) {

        // Check daily workload limits (corrected - using workstation IDs as strings)
        int currentDayAssignments = 0;
        String[] weeklyWorkstationIds = { "MMAU", "MMIU", "EU1", "EU2", "OHMAU", "OHMIU" };

        for (String workstationId : weeklyWorkstationIds) {
            if (solution.isAssignedWeekly(anaesthetistId, workstationId, day.getDayNumber(), week)) {
                currentDayAssignments++;
            }
        }

        // Limit to reasonable number of weekly assignments per day
        if (currentDayAssignments >= 2) {
            return true;
        }

        // Check specific workstation conflicts
        if ("EU1".equals(workstation.getId()) &&
                solution.isAssignedWeekly(anaesthetistId, "EU2", day.getDayNumber(), week)) {
            return true;
        }

        if ("EU2".equals(workstation.getId()) &&
                solution.isAssignedWeekly(anaesthetistId, "EU1", day.getDayNumber(), week)) {
            return true;
        }

        // Check OHMAU/OHMIU exclusivity
        if ("OHMAU".equals(workstation.getId()) &&
                solution.isAssignedWeekly(anaesthetistId, "OHMIU", day.getDayNumber(), week)) {
            return true;
        }

        if ("OHMIU".equals(workstation.getId()) &&
                solution.isAssignedWeekly(anaesthetistId, "OHMAU", day.getDayNumber(), week)) {
            return true;
        }

        // Check daily workload with actual workstation weights
        double currentWorkload = 0.0;
        for (Workstation ws : instance.getWeeklyWorkstations()) {
            if (solution.isAssignedWeekly(anaesthetistId, ws.getId(), day.getDayNumber(), week)) {
                currentWorkload += ws.getWeight();
            }
        }

        // Check if adding this workstation would exceed daily limit
        if (currentWorkload + workstation.getWeight() > instance.getMaxDailyWorkload()) {
            return true;
        }

        return false;
    }

    private boolean isAlreadyFullyAssigned(String anaesthetistId, PlanningDay day, int week,
            Solution solution, ProblemInstance instance) {

        // Check if anaesthetist is already assigned to monthly roster on this day
        boolean hasMonthlyAssignment = instance.getMonthlyWorkstations().stream()
                .anyMatch(w -> solution.isAssignedMonthly(anaesthetistId, w.getId(), day.getDayNumber()));

        if (hasMonthlyAssignment) {
            return true; // Cannot assign weekly if already has monthly assignment
        }

        // Check if already has maximum weekly assignments for the day
        int weeklyAssignmentCount = 0;
        for (Workstation workstation : instance.getWeeklyWorkstations()) {
            if (solution.isAssignedWeekly(anaesthetistId, workstation.getId(), day.getDayNumber(), week)) {
                weeklyAssignmentCount++;
            }
        }

        // Limit to maximum 2 weekly assignments per day
        return weeklyAssignmentCount >= 2;
    }

    private Map<String, Double> calculatePreferencePriorities(List<String> candidates, Workstation workstation,
            PlanningDay day, ProblemInstance instance,
            int week, Solution solution) {

        Map<String, Double> priorities = new HashMap<>();

        for (String candidateId : candidates) {
            // Base priority
            double priority = priorityCalculator.calculatePriority(candidateId, workstation,
                    day, instance, solution);

            // Preference-specific bonuses
            priority += calculatePreferenceBonus(candidateId, workstation, day, instance);

            // Request fulfillment bonus
            priority += calculateRequestFulfillmentBonus(candidateId, workstation, day, instance);

            // Workload balance consideration
            priority += calculateWeeklyWorkloadBalance(candidateId, week, solution, instance);

            // Special considerations for specific workstations
            priority += calculateWorkstationSpecificBonus(candidateId, workstation, day, instance, week, solution);

            priorities.put(candidateId, priority);
        }

        return priorities;
    }

    private double calculatePreferenceBonus(String anaesthetistId, Workstation workstation,
            PlanningDay day, ProblemInstance instance) {

        Anaesthetist anaesthetist = instance.getAnaesthetistById(anaesthetistId);

        if (anaesthetist.hasPreferenceFor(workstation.getId())) {
            return 40.0; // Strong bonus for preferred assignments
        } else if (anaesthetist.hasLessPreferenceFor(workstation.getId())) {
            return -20.0; // Penalty for less preferred assignments
        }

        return 0.0;
    }

    private double calculateRequestFulfillmentBonus(String anaesthetistId, Workstation workstation,
            PlanningDay day, ProblemInstance instance) {

        double bonus = 0.0;

        // Morning shift request fulfillment
        if (workstation.isMorningShift() &&
                instance.hasMorningShiftRequest(anaesthetistId, day.getDayNumber())) {
            bonus += 60.0; // High bonus for fulfilling shift requests
        }

        // Evening shift request fulfillment
        if (workstation.isEveningShift() &&
                instance.hasEveningShiftRequest(anaesthetistId, day.getDayNumber())) {
            bonus += 60.0;
        }

        // Special request considerations
        Optional<Request> request = instance.getRequest(anaesthetistId, day.getDayNumber());
        if (request.isPresent()) {
            if (request.get().isTeachingRequest() && workstation.isOfficeHoursShift()) {
                bonus += 30.0; // Teaching can be done during office hours
            }

            if (request.get().isExaminationRequest() || request.get().isDissertationRequest()) {
                // Penalty for major assignments on examination days
                if (workstation.isMajor()) {
                    bonus -= 50.0;
                }
            }
        }

        return bonus;
    }

    private double calculateWeeklyWorkloadBalance(String anaesthetistId, int week, Solution solution,
            ProblemInstance instance) {
        int currentWeeklyAssignments = 0;

        // Count all weekly assignments for this anaesthetist in this week
        for (PlanningDay day : instance.getWeekDays(week)) {
            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                if (solution.isAssignedWeekly(anaesthetistId, workstation.getId(), day.getDayNumber(), week)) {
                    currentWeeklyAssignments++;
                }
            }
        }

        // Bonus for anaesthetists with fewer assignments (promotes balance)
        int targetAssignments = 3; // Target 2-3 weekly assignments per anaesthetist per week
        if (currentWeeklyAssignments < targetAssignments) {
            return (targetAssignments - currentWeeklyAssignments) * 15.0;
        } else if (currentWeeklyAssignments > targetAssignments + 1) {
            return -20.0; // Penalty for overloading
        }

        return 0.0;
    }

    private double calculateWorkstationSpecificBonus(String anaesthetistId, Workstation workstation,
            PlanningDay day, ProblemInstance instance,
            int week, Solution solution) {

        double bonus = 0.0;
        Anaesthetist anaesthetist = instance.getAnaesthetistById(anaesthetistId);

        switch (workstation.getId()) {
            case "MMAU":
            case "MMIU":
                // Morning universal assignments - prefer anaesthetists with morning
                // availability
                if (isStartOfWeek(day) && !hasWeekendAssignment(anaesthetistId, instance, solution)) {
                    bonus += 10.0; // Fresh start for the week
                }
                break;

            case "EU1":
            case "EU2":
                // Evening universal - avoid if assigned to morning same day
                if (solution.isAssignedWeekly(anaesthetistId, "MMAU", day.getDayNumber(), week) ||
                        solution.isAssignedWeekly(anaesthetistId, "MMIU", day.getDayNumber(), week)) {
                    bonus -= 30.0; // Avoid morning-evening same day
                }
                break;

            case "OHMAU":
            case "OHMIU":
                // Office hours - good for senior anaesthetists
                if (anaesthetist.isSenior()) {
                    bonus += 15.0;
                }

                // Weekend/holiday office hours special handling
                if (day.isWeekendOrHoliday()) {
                    // Check weekend pairing
                    for (WeekendPair pair : instance.getWeekendPairs()) {
                        if (pair.containsDay(day.getDayNumber())) {
                            int otherDay = pair.getOtherDay(day.getDayNumber());
                            if (solution.isAssignedWeekly(anaesthetistId, workstation.getId(), otherDay, week)) {
                                bonus += 25.0; // Weekend pairing bonus
                            }
                        }
                    }
                }
                break;

            case "MPMIS":
                // Pediatric morning - prefer anaesthetists with pediatric experience
                if (anaesthetist.hasPreferenceFor("MPMIS")) {
                    bonus += 30.0;
                }
                break;

            default:
                // Default case for other workstations
                break;
        }

        return bonus;
    }

    private boolean isStartOfWeek(PlanningDay day) {
        // Check if this is Monday (day of week 1) or first day of the week
        return "Monday".equals(day.getDayOfWeek()) || day.getDayOfWeek().equals("Mon");
    }

    private boolean hasWeekendAssignment(String anaesthetistId, ProblemInstance instance, Solution solution) {
        // Check if anaesthetist had weekend assignments in monthly roster
        for (PlanningDay day : instance.getPlanningDays()) {
            if (day.isWeekendOrHoliday()) {
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    if (solution.isAssignedMonthly(anaesthetistId, workstation.getId(), day.getDayNumber())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Get current weekly assignment count for an anaesthetist in a specific week
     */
    private int getWeeklyAssignmentCount(String anaesthetistId, int week, Solution solution,
            ProblemInstance instance) {
        int count = 0;
        for (PlanningDay day : instance.getWeekDays(week)) {
            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                if (solution.isAssignedWeekly(anaesthetistId, workstation.getId(), day.getDayNumber(), week)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Check if anaesthetist has conflicting assignments on the same day
     */
    private boolean hasConflictingAssignments(String anaesthetistId, Workstation newWorkstation,
            PlanningDay day, int week, Solution solution,
            ProblemInstance instance) {

        // Check monthly conflicts
        for (Workstation monthlyWs : instance.getMonthlyWorkstations()) {
            if (solution.isAssignedMonthly(anaesthetistId, monthlyWs.getId(), day.getDayNumber())) {
                // Check if monthly and weekly assignments are compatible
                if (!areCompatibleAssignments(monthlyWs, newWorkstation)) {
                    return true;
                }
            }
        }

        // Check weekly conflicts
        for (Workstation weeklyWs : instance.getWeeklyWorkstations()) {
            if (!weeklyWs.getId().equals(newWorkstation.getId()) &&
                    solution.isAssignedWeekly(anaesthetistId, weeklyWs.getId(), day.getDayNumber(), week)) {

                if (!areCompatibleAssignments(weeklyWs, newWorkstation)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if two workstation assignments are compatible
     */
    private boolean areCompatibleAssignments(Workstation ws1, Workstation ws2) {
        // Define incompatible combinations

        // EU1 and EU2 are incompatible
        if (("EU1".equals(ws1.getId()) && "EU2".equals(ws2.getId())) ||
                ("EU2".equals(ws1.getId()) && "EU1".equals(ws2.getId()))) {
            return false;
        }

        // OHMAU and OHMIU are incompatible
        if (("OHMAU".equals(ws1.getId()) && "OHMIU".equals(ws2.getId())) ||
                ("OHMIU".equals(ws1.getId()) && "OHMAU".equals(ws2.getId()))) {
            return false;
        }

        // MMAU and MMIU are incompatible
        if (("MMAU".equals(ws1.getId()) && "MMIU".equals(ws2.getId())) ||
                ("MMIU".equals(ws1.getId()) && "MMAU".equals(ws2.getId()))) {
            return false;
        }

        // Check if both are major workstations (might exceed workload)
        if (ws1.isMajor() && ws2.isMajor()) {
            return ws1.getWeight() + ws2.getWeight() <= 2.0; // Assuming max daily workload is 2.0
        }

        return true; // Compatible by default
    }

    /**
     * Calculate the total weekly workload for an anaesthetist
     */
    private double calculateWeeklyWorkload(String anaesthetistId, int week, Solution solution,
            ProblemInstance instance) {
        double totalWorkload = 0.0;

        for (PlanningDay day : instance.getWeekDays(week)) {
            // Add monthly workload
            for (Workstation workstation : instance.getMonthlyWorkstations()) {
                if (solution.isAssignedMonthly(anaesthetistId, workstation.getId(), day.getDayNumber())) {
                    totalWorkload += workstation.getWeight();
                }
            }

            // Add weekly workload
            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                if (solution.isAssignedWeekly(anaesthetistId, workstation.getId(), day.getDayNumber(), week)) {
                    totalWorkload += workstation.getWeight();
                }
            }
        }

        return totalWorkload;
    }
}