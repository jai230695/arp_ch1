// File: src/main/java/com/arp/heuristics/monthly/SGOTAwareConstructor.java
package com.arp_1.heuristics.monthly;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.heuristics.base.GreedySelector;
import com.arp_1.heuristics.base.PriorityCalculator;
import com.arp_1.utils.LoggingUtils;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Specialized constructor for SGOT assignments with complex constraints
 */
public class SGOTAwareConstructor {
    private GreedySelector selector;
    private PriorityCalculator priorityCalculator;

    public SGOTAwareConstructor(GreedySelector selector) {
        this.selector = selector;
        this.priorityCalculator = new PriorityCalculator();
    }

    public Solution constructSGOTSchedule(ProblemInstance instance) {
        Solution solution = new Solution();
        Workstation sgot = instance.getWorkstationById("SGOT");

        if (sgot == null) {
            LoggingUtils.logWarning("SGOT workstation not found");
            return solution;
        }

        // Sort days for systematic assignment
        List<PlanningDay> sortedDays = new ArrayList<>(instance.getPlanningDays());
        sortedDays.sort(Comparator.comparingInt(PlanningDay::getDayNumber));

        for (PlanningDay day : sortedDays) {
            int required = instance.getWorkstationDemand("SGOT", day.getDayNumber());

            if (required > 0) {
                List<String> selectedAnaesthetists = selectSGOTAnaesthetists(day, instance, solution, required);

                for (String anaesthetistId : selectedAnaesthetists) {
                    solution.assignMonthly(anaesthetistId, "SGOT", day.getDayNumber());
                    enforceRestDay(solution, anaesthetistId, day.getDayNumber() + 1);

                    LoggingUtils.logAssignment("SGOT", day.getDayNumber(),
                            Arrays.asList(anaesthetistId), selector.getSelectorType());
                }
            }
        }

        return solution;
    }

    private List<String> selectSGOTAnaesthetists(PlanningDay day, ProblemInstance instance,
            Solution currentSolution, int required) {
        // Get eligible candidates
        List<String> candidates = getEligibleSGOTCandidates(day, instance, currentSolution);

        if (candidates.size() < required) {
            LoggingUtils.logWarning("Insufficient SGOT candidates for day " + day.getDayNumber() +
                    ": need " + required + ", have " + candidates.size());
            required = candidates.size();
        }

        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        // Calculate priorities with SGOT-specific considerations
        Map<String, Double> priorities = new HashMap<>();
        for (String candidateId : candidates) {
            double priority = calculateSGOTPriority(candidateId, day, instance, currentSolution);
            priorities.put(candidateId, priority);
        }

        // Use selector to choose candidates
        return selector.selectAnaesthetists(candidates, priorities, required);
    }

    private List<String> getEligibleSGOTCandidates(PlanningDay day, ProblemInstance instance,
            Solution currentSolution) {
        return instance.getAnaesthetists().stream()
                .filter(a -> a.isActive())
                .filter(a -> a.isQualifiedFor("SGOT"))
                .filter(a -> !instance.isAnaesthetistUnavailable(a.getId(), day.getDayNumber()))
                .filter(a -> !violatesSGOTConstraints(a.getId(), day, instance, currentSolution))
                .map(Anaesthetist::getId)
                .collect(Collectors.toList());
    }

    private boolean violatesSGOTConstraints(String anaesthetistId, PlanningDay day,
            ProblemInstance instance, Solution solution) {

        // HC3: No consecutive weekdays for SGOT
        if (!day.isWeekendOrHoliday()) {
            // Check previous day
            if (day.getDayNumber() > 1 &&
                    solution.isAssignedMonthly(anaesthetistId, "SGOT", day.getDayNumber() - 1)) {
                return true;
            }

            // Check next day constraint (don't assign if it would create future violation)
            if (day.getDayNumber() < 28) {
                PlanningDay nextDay = instance.getPlanningDay(day.getDayNumber() + 1);
                if (nextDay != null && !nextDay.isWeekendOrHoliday() &&
                        solution.isAssignedMonthly(anaesthetistId, "SGOT", day.getDayNumber() + 1)) {
                    return true;
                }
            }
        }

        // HC6: Cannot assign if rest day restriction exists
        if (solution.hasRestDayRestriction(anaesthetistId, day.getDayNumber())) {
            return true;
        }

        // Check for examination/dissertation conflicts (HC9)
        Optional<Request> request = instance.getRequest(anaesthetistId, day.getDayNumber());
        if (request.isPresent() &&
                (request.get().isExaminationRequest() || request.get().isDissertationRequest())) {
            return true;
        }

        return false;
    }

    private double calculateSGOTPriority(String anaesthetistId, PlanningDay day,
            ProblemInstance instance, Solution currentSolution) {

        // Base priority from standard calculation
        Workstation sgot = instance.getWorkstationById("SGOT");
        double priority = priorityCalculator.calculatePriority(anaesthetistId, sgot, day, instance, currentSolution);

        Anaesthetist anaesthetist = instance.getAnaesthetistById(anaesthetistId);

        // SGOT-specific adjustments

        // 1. Junior anaesthetists bonus (typically more available for SGOT)
        if (anaesthetist.isJunior()) {
            priority += 20.0;
        }

        // 2. Workload balance - penalize recent SGOT assignments
        int recentSGOTCount = 0;
        for (int checkDay = Math.max(1, day.getDayNumber() - 7); checkDay < day.getDayNumber(); checkDay++) {
            if (currentSolution.isAssignedMonthly(anaesthetistId, "SGOT", checkDay)) {
                recentSGOTCount++;
            }
        }
        priority -= recentSGOTCount * 15.0;

        // 3. Weekend pairing consideration
        if (day.isWeekendOrHoliday()) {
            for (WeekendPair pair : instance.getWeekendPairs()) {
                if (pair.containsDay(day.getDayNumber())) {
                    int otherDay = pair.getOtherDay(day.getDayNumber());
                    if (currentSolution.isAssignedMonthly(anaesthetistId, "SGOT", otherDay)) {
                        priority += 30.0; // Strong bonus for weekend pairing
                    }
                }
            }
        }

        // 4. Rest compliance bonus
        if (day.getDayNumber() > 1) {
            boolean hadRestYesterday = !currentSolution.isAssignedMonthlyAnyLocation(anaesthetistId,
                    day.getDayNumber() - 1);
            if (hadRestYesterday) {
                priority += 10.0;
            }
        }

        // 5. Request fulfillment
        if (day.getDayNumber() < 28) {
            // Bonus if assigning SGOT today fulfills morning/evening request tomorrow
            boolean tomorrowMorningRequest = instance.hasMorningShiftRequest(anaesthetistId, day.getDayNumber() + 1);
            boolean tomorrowEveningRequest = instance.hasEveningShiftRequest(anaesthetistId, day.getDayNumber() + 1);

            if (tomorrowMorningRequest || tomorrowEveningRequest) {
                priority += 25.0; // SGOT rest day can fulfill shift requests
            }
        }

        return priority;
    }

    private void enforceRestDay(Solution solution, String anaesthetistId, int day) {
        // HC6: Mandatory day off after SGOT
        if (day <= 28) {
            solution.addRestDayRestriction(anaesthetistId, day);
            LoggingUtils.logDebug("Enforced rest day for " + anaesthetistId + " on day " + day);
        }
    }

    /**
     * Check if SGOT assignment pattern is valid for the solution
     */
    public boolean validateSGOTPattern(Solution solution, ProblemInstance instance) {
        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getPlanningDays()) {
                if (solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", day.getDayNumber())) {
                    // Check consecutive weekday violation
                    if (!day.isWeekendOrHoliday() && day.getDayNumber() < 28) {
                        PlanningDay nextDay = instance.getPlanningDay(day.getDayNumber() + 1);
                        if (nextDay != null && !nextDay.isWeekendOrHoliday() &&
                                solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", day.getDayNumber() + 1)) {
                            return false;
                        }
                    }

                    // Check rest day enforcement
                    if (day.getDayNumber() < 28 &&
                            solution.isAssignedMonthlyAnyLocation(anaesthetist.getId(), day.getDayNumber() + 1)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
