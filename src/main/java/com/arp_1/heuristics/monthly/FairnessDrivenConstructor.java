// File: src/main/java/com/arp/heuristics/monthly/FairnessDrivenConstructor.java
package com.arp_1.heuristics.monthly;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.heuristics.base.GreedySelector;
import com.arp_1.heuristics.base.PriorityCalculator;
import com.arp_1.utils.LoggingUtils;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Constructor focused on fair workload distribution across all monthly
 * workstations
 */
public class FairnessDrivenConstructor {
    private GreedySelector selector;
    private PriorityCalculator priorityCalculator;
    private FairnessTracker fairnessTracker;

    public FairnessDrivenConstructor(GreedySelector selector) {
        this.selector = selector;
        this.priorityCalculator = new PriorityCalculator();
    }

    public Solution completeMonthlyRoster(ProblemInstance instance, Solution partialSolution) {
        // Initialize fairness tracker with historical data and current assignments
        fairnessTracker = new FairnessTracker(instance, partialSolution);

        LoggingUtils.logInfo("Completing monthly roster with fairness-driven approach");

        // Get remaining workstations (excluding SGOT which should already be assigned)
        List<Workstation> remainingWorkstations = instance.getMonthlyWorkstations().stream()
                .filter(w -> !"SGOT".equals(w.getId()))
                .collect(Collectors.toList());

        // Process workstations in priority order
        List<Workstation> prioritizedWorkstations = prioritizeWorkstationsByConstraints(remainingWorkstations);

        for (Workstation workstation : prioritizedWorkstations) {
            LoggingUtils.logInfo("Processing workstation: " + workstation.getId());
            assignWorkstationWithFairness(workstation, instance, partialSolution);
        }

        LoggingUtils.logInfo("Monthly roster completion finished");
        return partialSolution;
    }

    private List<Workstation> prioritizeWorkstationsByConstraints(List<Workstation> workstations) {
        return workstations.stream()
                .sorted((w1, w2) -> {
                    // Priority order: CICU (consecutive requirements) > SICU > Others > CGOT > PWOT
                    int priority1 = getWorkstationPriority(w1);
                    int priority2 = getWorkstationPriority(w2);
                    return Integer.compare(priority1, priority2);
                })
                .collect(Collectors.toList());
    }

    private int getWorkstationPriority(Workstation workstation) {
        switch (workstation.getId()) {
            case "CICU":
                return 1; // Highest priority (consecutive day requirements)
            case "SICU":
                return 2; // Second (specialist requirements)
            case "CCT":
            case "SCT":
                return 3; // Specialist workstations
            case "CGOT":
                return 4; // General consultant
            case "PWOT":
                return 5; // Lowest priority
            default:
                return 6;
        }
    }

    private void assignWorkstationWithFairness(Workstation workstation, ProblemInstance instance,
            Solution solution) {

        for (PlanningDay day : instance.getPlanningDays()) {
            int required = instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());

            if (required > 0) {
                assignWorkstationDay(workstation, day, required, instance, solution);
            }
        }
    }

    private void assignWorkstationDay(Workstation workstation, PlanningDay day, int required,
            ProblemInstance instance, Solution solution) {

        // Get eligible candidates
        List<String> candidates = getEligibleCandidates(workstation, day, instance, solution);

        if (candidates.size() < required) {
            LoggingUtils.logWarning("Insufficient candidates for " + workstation.getId() +
                    " on day " + day.getDayNumber() + ": need " + required + ", have " + candidates.size());
            required = Math.min(required, candidates.size());
        }

        if (candidates.isEmpty()) {
            LoggingUtils.logWarning("No eligible candidates for " + workstation.getId() +
                    " on day " + day.getDayNumber());
            return;
        }

        // Calculate fairness-weighted priorities
        Map<String, Double> priorities = calculateFairnessPriorities(candidates, workstation, day, instance, solution);

        // Select anaesthetists using the configured selector
        List<String> selectedAnaesthetists = selector.selectAnaesthetists(candidates, priorities, required);

        // Make assignments and update fairness tracker
        for (String anaesthetistId : selectedAnaesthetists) {
            solution.assignMonthly(anaesthetistId, workstation.getId(), day.getDayNumber());
            fairnessTracker.updateAssignment(anaesthetistId, workstation.getId(), day);

            // Handle special constraints
            handleSpecialAssignmentRules(anaesthetistId, workstation, day, instance, solution);
        }

        LoggingUtils.logAssignment(workstation.getId(), day.getDayNumber(),
                selectedAnaesthetists, selector.getSelectorType());
    }

    private List<String> getEligibleCandidates(Workstation workstation, PlanningDay day,
            ProblemInstance instance, Solution solution) {

        return instance.getAnaesthetists().stream()
                .filter(a -> a.isActive())
                .filter(a -> a.isQualifiedFor(workstation.getId()))
                .filter(a -> !instance.isAnaesthetistUnavailable(a.getId(), day.getDayNumber()))
                .filter(a -> !solution.hasRestDayRestriction(a.getId(), day.getDayNumber()))
                .filter(a -> !violatesHardConstraints(a.getId(), workstation, day, instance, solution))
                .map(Anaesthetist::getId)
                .collect(Collectors.toList());
    }

    private boolean violatesHardConstraints(String anaesthetistId, Workstation workstation,
            PlanningDay day, ProblemInstance instance, Solution solution) {

        // HC8: Daily workload limits
        double currentWorkload = 0.0;
        for (Workstation w : instance.getMonthlyWorkstations()) {
            if (solution.isAssignedMonthly(anaesthetistId, w.getId(), day.getDayNumber())) {
                currentWorkload += w.getWeight();
            }
        }

        if (currentWorkload + workstation.getWeight() > instance.getMaxDailyWorkload()) {
            return true;
        }

        // HC9: Invalid combinations
        if ("SICU".equals(workstation.getId()) && !day.isWeekendOrHoliday()) {
            // SICU + SGOT combination not allowed on weekdays
            if (solution.isAssignedMonthly(anaesthetistId, "SGOT", day.getDayNumber())) {
                return true;
            }
        }

        // HC4: Special qualification rules for SICU on weekends
        if ("SICU".equals(workstation.getId()) && day.isWeekendOrHoliday()) {
            Anaesthetist anaesthetist = instance.getAnaesthetistById(anaesthetistId);
            if (!anaesthetist.hasPreferenceFor("SICU")) {
                return true; // SICU on weekends requires preference level 1
            }
        }

        return false;
    }

    private Map<String, Double> calculateFairnessPriorities(List<String> candidates, Workstation workstation,
            PlanningDay day, ProblemInstance instance, Solution solution) {

        Map<String, Double> priorities = new HashMap<>();

        for (String candidateId : candidates) {
            // Base priority calculation
            double priority = priorityCalculator.calculatePriority(candidateId, workstation, day, instance, solution);

            // Fairness adjustment based on current assignment distribution
            double fairnessBonus = fairnessTracker.calculateFairnessBonus(candidateId, workstation.getId());
            priority += fairnessBonus;

            // Weekend fairness adjustment
            if (day.isWeekendOrHoliday()) {
                double weekendFairnessBonus = fairnessTracker.calculateWeekendFairnessBonus(candidateId,
                        workstation.getId());
                priority += weekendFairnessBonus;
            }

            // Pre-holiday fairness adjustment
            if (day.isPreHoliday()) {
                double preHolidayFairnessBonus = fairnessTracker.calculatePreHolidayFairnessBonus(candidateId,
                        workstation.getId());
                priority += preHolidayFairnessBonus;
            }

            // Special workstation considerations
            priority += calculateWorkstationSpecificPriority(candidateId, workstation, day, instance, solution);

            priorities.put(candidateId, priority);
        }

        return priorities;
    }

    private double calculateWorkstationSpecificPriority(String anaesthetistId, Workstation workstation,
            PlanningDay day, ProblemInstance instance, Solution solution) {
        double bonus = 0.0;

        switch (workstation.getId()) {
            case "CICU":
                // CICU prefers consecutive assignments
                bonus += calculateCICUConsecutiveBonus(anaesthetistId, day, solution);
                break;

            case "SICU":
                // SICU requires at least one junior anaesthetist
                Anaesthetist anaesthetist = instance.getAnaesthetistById(anaesthetistId);
                if (anaesthetist.isJunior()) {
                    // Check if other junior already assigned
                    boolean juniorAlreadyAssigned = instance.getJuniorAnaesthetists().stream()
                            .anyMatch(a -> solution.isAssignedMonthly(a.getId(), "SICU", day.getDayNumber()));

                    if (!juniorAlreadyAssigned) {
                        bonus += 50.0; // Strong bonus for first junior
                    }
                }
                break;

            case "CGOT":
            case "PWOT":
                // Weekend pairing bonus
                if (day.isWeekendOrHoliday()) {
                    bonus += calculateWeekendPairingBonus(anaesthetistId, workstation.getId(), day, instance, solution);
                }
                break;
        }

        return bonus;
    }

    private double calculateCICUConsecutiveBonus(String anaesthetistId, PlanningDay day, Solution solution) {
        double bonus = 0.0;

        // Check if assigning today would create/continue consecutive CICU assignments
        boolean previousCICU = day.getDayNumber() > 1 &&
                solution.isAssignedMonthly(anaesthetistId, "CICU", day.getDayNumber() - 1);

        if (previousCICU) {
            bonus += 30.0; // Continue consecutive assignment
        }

        // Check if assignment would enable future consecutive assignment
        boolean nextDayAvailable = day.getDayNumber() < 28 &&
                !solution.isAssignedMonthlyAnyLocation(anaesthetistId, day.getDayNumber() + 1);

        if (nextDayAvailable) {
            bonus += 15.0; // Potential for consecutive assignment
        }

        return bonus;
    }

    private double calculateWeekendPairingBonus(String anaesthetistId, String workstationId,
            PlanningDay day, ProblemInstance instance, Solution solution) {

        for (WeekendPair pair : instance.getWeekendPairs()) {
            if (pair.containsDay(day.getDayNumber())) {
                int otherDay = pair.getOtherDay(day.getDayNumber());
                if (solution.isAssignedMonthly(anaesthetistId, workstationId, otherDay)) {
                    return 40.0; // Strong bonus for completing weekend pair
                }
            }
        }

        return 0.0;
    }

    private void handleSpecialAssignmentRules(String anaesthetistId, Workstation workstation,
            PlanningDay day, ProblemInstance instance, Solution solution) {

        // Handle weekend pairing requirements (HC7)
        if (day.isWeekendOrHoliday()) {
            String[] pairingWorkstations = { "CGOT", "SGOT", "SICU" };

            if (Arrays.asList(pairingWorkstations).contains(workstation.getId())) {
                for (WeekendPair pair : instance.getWeekendPairs()) {
                    if (pair.containsDay(day.getDayNumber())) {
                        int otherDay = pair.getOtherDay(day.getDayNumber());
                        // Auto-assign to same workstation on other day of weekend pair
                        solution.assignMonthly(anaesthetistId, workstation.getId(), otherDay);
                        LoggingUtils.logDebug("Auto-assigned weekend pair: " + anaesthetistId +
                                " to " + workstation.getId() + " on day " + otherDay);
                    }
                }
            }
        }

        // Handle workstation pairing requirements (HC11, SC4)
        for (WorkstationPair wsP : instance.getWorkstationPairs()) {
            if (wsP.getWorkstation1().equals(workstation.getId()) ||
                    wsP.getWorkstation2().equals(workstation.getId())) {

                String pairedWorkstation = wsP.getWorkstation1().equals(workstation.getId()) ? wsP.getWorkstation2()
                        : wsP.getWorkstation1();

                // Check if should auto-assign to paired workstation
                if (shouldAutoAssignPair(anaesthetistId, pairedWorkstation, day, instance, solution)) {
                    solution.assignMonthly(anaesthetistId, pairedWorkstation, day.getDayNumber());
                    LoggingUtils.logDebug("Auto-assigned workstation pair: " + anaesthetistId +
                            " to " + pairedWorkstation + " on day " + day.getDayNumber());
                }
            }
        }
    }

    private boolean shouldAutoAssignPair(String anaesthetistId, String workstationId,
            PlanningDay day, ProblemInstance instance, Solution solution) {

        // Check if anaesthetist is qualified for the paired workstation
        Anaesthetist anaesthetist = instance.getAnaesthetistById(anaesthetistId);
        if (!anaesthetist.isQualifiedFor(workstationId)) {
            return false;
        }

        // Check if workstation has demand
        int demand = instance.getWorkstationDemand(workstationId, day.getDayNumber());
        if (demand <= 0) {
            return false;
        }

        // Check if workstation is already fully assigned
        int currentAssigned = 0;
        for (Anaesthetist a : instance.getAnaesthetists()) {
            if (solution.isAssignedMonthly(a.getId(), workstationId, day.getDayNumber())) {
                currentAssigned++;
            }
        }

        if (currentAssigned >= demand) {
            return false;
        }

        // Check workload constraints
        Workstation ws = instance.getWorkstationById(workstationId);
        double currentWorkload = solution.getDayWorkload(anaesthetistId, day.getDayNumber());

        return currentWorkload + ws.getWeight() <= instance.getMaxDailyWorkload();
    }

    // Inner class for tracking fairness across assignments
    private static class FairnessTracker {
        private Map<String, Map<String, Integer>> currentAssignments;
        private Map<String, Map<String, Integer>> historicalAssignments;
        private Map<String, Map<String, Integer>> weekendAssignments;
        private Map<String, Map<String, Integer>> preHolidayAssignments;

        public FairnessTracker(ProblemInstance instance, Solution currentSolution) {
            this.currentAssignments = new HashMap<>();
            this.historicalAssignments = new HashMap<>();
            this.weekendAssignments = new HashMap<>();
            this.preHolidayAssignments = new HashMap<>();

            // Initialize with historical data
            for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                String anaesthetistId = anaesthetist.getId();

                Map<String, Integer> historical = new HashMap<>();
                Map<String, Integer> weekend = new HashMap<>();
                Map<String, Integer> preHoliday = new HashMap<>();
                Map<String, Integer> current = new HashMap<>();

                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    String workstationId = workstation.getId();

                    historical.put(workstationId,
                            instance.getHistoricalAssignments(anaesthetistId, workstationId));
                    weekend.put(workstationId,
                            instance.getHistoricalWeekendAssignments(anaesthetistId, workstationId));
                    preHoliday.put(workstationId,
                            instance.getHistoricalPreHolidayAssignments(anaesthetistId, workstationId));
                    current.put(workstationId,
                            currentSolution.countMonthlyAssignments(anaesthetistId, workstationId));
                }

                this.historicalAssignments.put(anaesthetistId, historical);
                this.weekendAssignments.put(anaesthetistId, weekend);
                this.preHolidayAssignments.put(anaesthetistId, preHoliday);
                this.currentAssignments.put(anaesthetistId, current);
            }
        }

        public void updateAssignment(String anaesthetistId, String workstationId, PlanningDay day) {
            // Update current assignments
            currentAssignments.get(anaesthetistId).merge(workstationId, 1, Integer::sum);

            // Update weekend assignments if applicable
            if (day.isWeekendOrHoliday()) {
                weekendAssignments.get(anaesthetistId).merge(workstationId, 1, Integer::sum);
            }

            // Update pre-holiday assignments if applicable
            if (day.isPreHoliday()) {
                preHolidayAssignments.get(anaesthetistId).merge(workstationId, 1, Integer::sum);
            }
        }

        public double calculateFairnessBonus(String anaesthetistId, String workstationId) {
            int currentCount = currentAssignments.get(anaesthetistId).get(workstationId);
            int historicalCount = historicalAssignments.get(anaesthetistId).get(workstationId);
            int totalCount = currentCount + historicalCount;

            // Calculate average total assignments for this workstation
            double averageTotal = currentAssignments.keySet().stream()
                    .mapToInt(id -> currentAssignments.get(id).get(workstationId) +
                            historicalAssignments.get(id).get(workstationId))
                    .average()
                    .orElse(0.0);

            // Bonus for anaesthetists with fewer assignments (promotes fairness)
            double deviation = totalCount - averageTotal;
            return Math.max(0, -deviation * 20.0); // 20 points per assignment below average
        }

        public double calculateWeekendFairnessBonus(String anaesthetistId, String workstationId) {
            int weekendCount = weekendAssignments.get(anaesthetistId).get(workstationId);

            double averageWeekend = weekendAssignments.keySet().stream()
                    .mapToInt(id -> weekendAssignments.get(id).get(workstationId))
                    .average()
                    .orElse(0.0);

            double deviation = weekendCount - averageWeekend;
            return Math.max(0, -deviation * 15.0); // 15 points per weekend assignment below average
        }

        public double calculatePreHolidayFairnessBonus(String anaesthetistId, String workstationId) {
            int preHolidayCount = preHolidayAssignments.get(anaesthetistId).get(workstationId);

            double averagePreHoliday = preHolidayAssignments.keySet().stream()
                    .mapToInt(id -> preHolidayAssignments.get(id).get(workstationId))
                    .average()
                    .orElse(0.0);

            double deviation = preHolidayCount - averagePreHoliday;
            return Math.max(0, -deviation * 10.0); // 10 points per pre-holiday assignment below average
        }
    }
}
