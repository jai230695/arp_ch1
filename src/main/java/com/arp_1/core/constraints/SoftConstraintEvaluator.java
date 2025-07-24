// File: src/main/java/com/arp/core/constraints/SoftConstraintEvaluator.java
package com.arp_1.core.constraints;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import java.util.*;

public class SoftConstraintEvaluator {

    public List<ConstraintViolation> evaluateAllSoftConstraints(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        // Evaluate each soft constraint
        violations.addAll(evaluateSC1_RestDayCompliance(solution, instance));
        violations.addAll(evaluateSC2_NoCallRequests(solution, instance));
        violations.addAll(evaluateSC3_ShiftRequests(solution, instance));
        violations.addAll(evaluateSC4_PreferredPairings(solution, instance));
        violations.addAll(evaluateSC5_FairWorkloadDistribution(solution, instance));
        violations.addAll(evaluateSC6_FairWeekendDistribution(solution, instance));
        violations.addAll(evaluateSC7_FairPreHolidayDistribution(solution, instance));
        violations.addAll(evaluateSC8_PreferenceAccommodation(solution, instance));
        violations.addAll(evaluateSC9_ConsecutiveDayAssignments(solution, instance));
        violations.addAll(evaluateSC10_UndesiredCombinations(solution, instance));

        return violations;
    }

    private List<ConstraintViolation> evaluateSC1_RestDayCompliance(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int restViolations = 0;

            // Check SGOT rest violations (3-day window)
            for (PlanningDay day : instance.getPlanningDays()) {
                if (solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", day.getDayNumber())) {
                    // Check 3-day window around SGOT assignment
                    int sgotCount = 0;
                    int startDay = Math.max(1, day.getDayNumber() - 1);
                    int endDay = Math.min(28, day.getDayNumber() + 1);

                    for (int checkDay = startDay; checkDay <= endDay; checkDay++) {
                        if (solution.isAssignedMonthlyAnyLocation(anaesthetist.getId(), checkDay)) {
                            sgotCount++;
                        }
                    }

                    if (sgotCount >= 3) {
                        restViolations++;
                    }
                }
            }

            // Check CGOT rest violations (2-day window)
            for (PlanningDay day : instance.getPlanningDays()) {
                if (solution.isAssignedMonthly(anaesthetist.getId(), "CGOT", day.getDayNumber())) {
                    int cgotCount = 0;
                    int startDay = Math.max(1, day.getDayNumber());
                    int endDay = Math.min(28, day.getDayNumber() + 2);

                    for (int checkDay = startDay; checkDay <= endDay; checkDay++) {
                        if (solution.isAssignedMonthly(anaesthetist.getId(), "CGOT", checkDay)) {
                            cgotCount++;
                        }
                    }

                    if (cgotCount >= 2) {
                        restViolations++;
                    }
                }
            }

            // Check CICU consecutive day violations
            for (int day = 1; day < 28; day++) {
                boolean currentAssigned = solution.isAssignedMonthly(anaesthetist.getId(), "CICU", day);
                boolean nextAssigned = solution.isAssignedMonthly(anaesthetist.getId(), "CICU", day + 1);

                // CICU should be assigned in consecutive pairs, violation if isolated
                if (currentAssigned && !nextAssigned &&
                        (day == 1 || !solution.isAssignedMonthly(anaesthetist.getId(), "CICU", day - 1))) {
                    restViolations++;
                }
            }

            if (restViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC1,
                        "Rest day compliance violations",
                        anaesthetist.getId(), null, -1, restViolations));
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateSC2_NoCallRequests(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int noCallViolations = 0;

            for (PlanningDay day : instance.getPlanningDays()) {
                if (instance.hasNoCallRequest(anaesthetist.getId(), day.getDayNumber())) {
                    // Check if assigned to any monthly workstation (on-call)
                    boolean assignedToOnCall = instance.getMonthlyWorkstations().stream()
                            .anyMatch(w -> solution.isAssignedMonthly(anaesthetist.getId(), w.getId(),
                                    day.getDayNumber()));

                    if (assignedToOnCall) {
                        noCallViolations++;
                    }
                }
            }

            if (noCallViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC2,
                        "No call request violations",
                        anaesthetist.getId(), null, -1, noCallViolations));
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateSC3_ShiftRequests(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int shiftRequestViolations = 0;

            for (PlanningDay day : instance.getPlanningDays()) {
                boolean hasMorningRequest = instance.hasMorningShiftRequest(anaesthetist.getId(), day.getDayNumber());
                boolean hasEveningRequest = instance.hasEveningShiftRequest(anaesthetist.getId(), day.getDayNumber());

                if (hasMorningRequest || hasEveningRequest) {
                    // Check if assigned to requested shift type
                    boolean assignedToMorning = false;
                    boolean assignedToEvening = false;

                    int week = day.getWeek();
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(), day.getDayNumber(),
                                week)) {
                            if (workstation.isMorningShift()) {
                                assignedToMorning = true;
                            } else if (workstation.isEveningShift()) {
                                assignedToEvening = true;
                            }
                        }
                    }

                    // Special case: if assigned to SGOT on previous day, can fulfill morning
                    // request next day
                    if (hasMorningRequest && day.getDayNumber() > 1) {
                        if (solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", day.getDayNumber() - 1)) {
                            assignedToMorning = true; // SGOT rest day counts as morning availability
                        }
                    }

                    if ((hasMorningRequest && !assignedToMorning) || (hasEveningRequest && !assignedToEvening)) {
                        shiftRequestViolations++;
                    }
                }
            }

            if (shiftRequestViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC3,
                        "Shift request violations",
                        anaesthetist.getId(), null, -1, shiftRequestViolations));
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateSC4_PreferredPairings(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int pairingViolations = 0;

            for (WorkstationPair pair : instance.getWorkstationPairs()) {
                for (PlanningDay day : instance.getPlanningDays()) {
                    if (!day.isWeekendOrHoliday()) {
                        boolean ws1Assigned = solution.isAssignedMonthly(anaesthetist.getId(),
                                pair.getWorkstation1(), day.getDayNumber());
                        boolean ws2Assigned = solution.isAssignedMonthly(anaesthetist.getId(),
                                pair.getWorkstation2(), day.getDayNumber());

                        // Violation if assigned to one but not the other (should be paired)
                        if (ws1Assigned != ws2Assigned) {
                            pairingViolations++;
                        }
                    }
                }
            }

            if (pairingViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC4,
                        "Preferred pairing violations",
                        anaesthetist.getId(), null, -1, pairingViolations));
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateSC5_FairWorkloadDistribution(Solution solution,
            ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Workstation workstation : instance.getMonthlyWorkstations()) {
            List<Integer> assignmentCounts = new ArrayList<>();

            for (Anaesthetist anaesthetist : instance.getQualifiedAnaesthetists(workstation.getId())) {
                int currentAssignments = solution.countMonthlyAssignments(anaesthetist.getId(), workstation.getId());
                int historicalAssignments = instance.getHistoricalAssignments(anaesthetist.getId(),
                        workstation.getId());
                int totalAssignments = currentAssignments + historicalAssignments;

                assignmentCounts.add(totalAssignments);
            }

            if (!assignmentCounts.isEmpty()) {
                int max = Collections.max(assignmentCounts);
                int min = Collections.min(assignmentCounts);
                int range = max - min;

                if (range > 0) {
                    violations.add(new ConstraintViolation(
                            ConstraintType.SC5,
                            String.format("Workload distribution range for %s: %d (max=%d, min=%d)",
                                    workstation.getId(), range, max, min),
                            null, workstation.getId(), -1, range));
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateSC6_FairWeekendDistribution(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Workstation workstation : instance.getMonthlyWorkstations()) {
            if ("SICU".equals(workstation.getId())) {
                // SICU has special weekend rules (exclude weekend pairs)
                continue;
            }

            List<Integer> weekendCounts = new ArrayList<>();

            for (Anaesthetist anaesthetist : instance.getQualifiedAnaesthetists(workstation.getId())) {
                int currentWeekendAssignments = solution.countWeekendAssignments(
                        anaesthetist.getId(), workstation.getId(), instance.getWeekendHolidays());
                int historicalWeekendAssignments = instance.getHistoricalWeekendAssignments(
                        anaesthetist.getId(), workstation.getId());
                int totalWeekendAssignments = currentWeekendAssignments + historicalWeekendAssignments;

                weekendCounts.add(totalWeekendAssignments);
            }

            if (!weekendCounts.isEmpty()) {
                int max = Collections.max(weekendCounts);
                int min = Collections.min(weekendCounts);
                int range = max - min;

                if (range > 0) {
                    violations.add(new ConstraintViolation(
                            ConstraintType.SC6,
                            String.format("Weekend distribution range for %s: %d (max=%d, min=%d)",
                                    workstation.getId(), range, max, min),
                            null, workstation.getId(), -1, range));
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateSC7_FairPreHolidayDistribution(Solution solution,
            ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Workstation workstation : instance.getMonthlyWorkstations()) {
            if ("CICU".equals(workstation.getId())) {
                // CICU excluded from pre-holiday distribution
                continue;
            }

            List<Integer> preHolidayCounts = new ArrayList<>();

            for (Anaesthetist anaesthetist : instance.getQualifiedAnaesthetists(workstation.getId())) {
                int currentPreHolidayAssignments = solution.countPreHolidayAssignments(
                        anaesthetist.getId(), workstation.getId(), instance.getPreHolidays());
                int historicalPreHolidayAssignments = instance.getHistoricalPreHolidayAssignments(
                        anaesthetist.getId(), workstation.getId());
                int totalPreHolidayAssignments = currentPreHolidayAssignments + historicalPreHolidayAssignments;

                preHolidayCounts.add(totalPreHolidayAssignments);
            }

            if (!preHolidayCounts.isEmpty()) {
                int max = Collections.max(preHolidayCounts);
                int min = Collections.min(preHolidayCounts);
                int range = max - min;

                if (range > 0) {
                    violations.add(new ConstraintViolation(
                            ConstraintType.SC7,
                            String.format("Pre-holiday distribution range for %s: %d (max=%d, min=%d)",
                                    workstation.getId(), range, max, min),
                            null, workstation.getId(), -1, range));
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateSC8_PreferenceAccommodation(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int preferenceViolations = 0;

            // Check monthly workstations
            for (Workstation workstation : instance.getMonthlyWorkstations()) {
                if (anaesthetist.hasLessPreferenceFor(workstation.getId())) {
                    int assignmentCount = 0;

                    if ("SICU".equals(workstation.getId())) {
                        // SICU: count only non-weekend assignments
                        for (PlanningDay day : instance.getPlanningDays()) {
                            if (!day.isWeekendOrHoliday() &&
                                    solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(),
                                            day.getDayNumber())) {
                                assignmentCount++;
                            }
                        }
                    } else {
                        // Other workstations: count all assignments
                        assignmentCount = solution.countMonthlyAssignments(anaesthetist.getId(), workstation.getId());
                    }

                    preferenceViolations += assignmentCount;
                }
            }

            // Check weekly workstations
            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                if (anaesthetist.hasLessPreferenceFor(workstation.getId())) {
                    int assignmentCount = 0;

                    if ("OHMAU".equals(workstation.getId())) {
                        // OHMAU: count only non-weekend assignments
                        for (int week = 1; week <= 4; week++) {
                            for (PlanningDay day : instance.getWeekDays(week)) {
                                if (!day.isWeekendOrHoliday() &&
                                        solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                                day.getDayNumber(), week)) {
                                    assignmentCount++;
                                }
                            }
                        }
                    } else {
                        // Other weekly workstations: count all assignments
                        for (int week = 1; week <= 4; week++) {
                            assignmentCount += solution.countWeeklyAssignments(anaesthetist.getId(),
                                    workstation.getId(), week);
                        }
                    }

                    preferenceViolations += assignmentCount;
                }
            }

            if (preferenceViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC8,
                        "Preference accommodation violations",
                        anaesthetist.getId(), null, -1, preferenceViolations));
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateSC9_ConsecutiveDayAssignments(Solution solution,
            ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int consecutiveViolations = 0;

            // Check CICU consecutive day requirements (2-day and 3-day patterns)
            for (int day = 1; day < 28; day++) {
                boolean currentAssigned = solution.isAssignedMonthly(anaesthetist.getId(), "CICU", day);
                boolean nextAssigned = solution.isAssignedMonthly(anaesthetist.getId(), "CICU", day + 1);

                // Violation if assigned to one day but not consecutive day
                if (currentAssigned != nextAssigned) {
                    consecutiveViolations++;
                }
            }

            // Check 3-day consecutive patterns for CICU
            for (int day = 1; day <= 26; day++) {
                boolean day1Assigned = solution.isAssignedMonthly(anaesthetist.getId(), "CICU", day);
                boolean day2Assigned = solution.isAssignedMonthly(anaesthetist.getId(), "CICU", day + 1);
                boolean day3Assigned = solution.isAssignedMonthly(anaesthetist.getId(), "CICU", day + 2);

                // Check if pattern is inconsistent (should be all assigned or all unassigned)
                if (!(day1Assigned == day2Assigned && day2Assigned == day3Assigned)) {
                    consecutiveViolations++;
                }
            }

            if (consecutiveViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC9,
                        "Consecutive day assignment violations",
                        anaesthetist.getId(), "CICU", -1, consecutiveViolations));
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateSC10_UndesiredCombinations(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int undesiredViolations = 0;

            for (PlanningDay day : instance.getPlanningDays()) {
                // Check if assigned to monthly workstation on days with
                // examination/dissertation requests
                Optional<Request> request = instance.getRequest(anaesthetist.getId(), day.getDayNumber());
                if (request.isPresent() &&
                        (request.get().isExaminationRequest() || request.get().isDissertationRequest() ||
                                request.get().isTeachingRequest())) {

                    // Count assignments to major monthly workstations
                    String[] majorWorkstations = { "CGOT", "SGOT", "PWOT", "CICU", "SICU" };
                    for (String workstationId : majorWorkstations) {
                        if (solution.isAssignedMonthly(anaesthetist.getId(), workstationId, day.getDayNumber())) {
                            undesiredViolations++;
                        }
                    }

                    // Count assignments to major weekly workstations (excluding OHMIU, MMIU)
                    int week = day.getWeek();
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        if (!workstation.getId().equals("OHMIU") && !workstation.getId().equals("MMIU")) {
                            if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                    day.getDayNumber(), week)) {
                                undesiredViolations++;
                            }
                        }
                    }
                }

                // Check undesired location combinations
                // Example: SGOT + certain weekly locations might be undesired
                if (solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", day.getDayNumber())) {
                    int week = day.getWeek();
                    String[] undesiredWithSGOT = { "MMAU" };
                    for (String workstationId : undesiredWithSGOT) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), workstationId, day.getDayNumber(), week)) {
                            undesiredViolations++;
                        }
                    }
                }
            }

            if (undesiredViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC10,
                        "Undesired combination violations",
                        anaesthetist.getId(), null, -1, undesiredViolations));
            }
        }

        return violations;
    }

    public double calculateTotalSoftConstraintPenalty(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = evaluateAllSoftConstraints(solution, instance);

        return violations.stream()
                .mapToDouble(ConstraintViolation::getPenalty)
                .sum();
    }

    public Map<ConstraintType, Integer> getSoftConstraintViolationCounts(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = evaluateAllSoftConstraints(solution, instance);
        Map<ConstraintType, Integer> counts = new HashMap<>();

        // Initialize all soft constraint counts to 0
        for (ConstraintType type : ConstraintType.values()) {
            if (type.isSoftConstraint()) {
                counts.put(type, 0);
            }
        }

        // Count violations
        for (ConstraintViolation violation : violations) {
            counts.put(violation.getConstraintType(),
                    counts.get(violation.getConstraintType()) + violation.getViolationCount());
        }

        return counts;
    }

    public void printSoftConstraintViolations(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = evaluateAllSoftConstraints(solution, instance);
        Map<ConstraintType, Integer> counts = getSoftConstraintViolationCounts(solution, instance);

        System.out.println("Soft Constraint Violations:");
        System.out.println("===========================");

        double totalPenalty = 0.0;
        for (ConstraintType type : ConstraintType.values()) {
            if (type.isSoftConstraint()) {
                int count = counts.get(type);
                double penalty = count * type.getDefaultPenaltyWeight();
                totalPenalty += penalty;

                System.out.printf("%-4s: %3d violations, penalty: %6.0f (weight: %2d)\n",
                        type.name(), count, penalty, type.getDefaultPenaltyWeight());
            }
        }

        System.out.println("----------------------------");
        System.out.printf("Total Soft Constraint Penalty: %.0f\n", totalPenalty);

        if (!violations.isEmpty()) {
            System.out.println("\nDetailed Violations:");
            for (ConstraintViolation violation : violations) {
                System.out.println("  " + violation);
            }
        }
    }
}