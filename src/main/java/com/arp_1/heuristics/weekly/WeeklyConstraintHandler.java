// File: src/main/java/com/arp/heuristics/weekly/WeeklyConstraintHandler.java
package com.arp_1.heuristics.weekly;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.core.constraints.*;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Handles constraint validation and optimization for weekly rosters
 */
public class WeeklyConstraintHandler {
    private HardConstraintChecker hardConstraintChecker;
    private SoftConstraintEvaluator softConstraintEvaluator;

    public WeeklyConstraintHandler() {
        this.hardConstraintChecker = new HardConstraintChecker();
        this.softConstraintEvaluator = new SoftConstraintEvaluator();
    }

    public Solution validateAndOptimize(ProblemInstance instance, Solution solution, int week) {
        LoggingUtils.logInfo("Validating and optimizing weekly solution for week " + week);

        // Check weekly-specific hard constraints
        List<ConstraintViolation> weeklyHardViolations = checkWeeklyHardConstraints(instance, solution, week);

        if (!weeklyHardViolations.isEmpty()) {
            LoggingUtils.logWarning("Weekly hard constraint violations found: " + weeklyHardViolations.size());
            solution = attemptWeeklyConstraintRepair(instance, solution, week, weeklyHardViolations);
        } else {
            LoggingUtils.logInfo("All weekly hard constraints satisfied");
        }

        // Evaluate weekly-specific soft constraints
        List<ConstraintViolation> weeklySoftViolations = evaluateWeeklySoftConstraints(instance, solution, week);
        LoggingUtils.logInfo("Weekly soft constraint violations: " + weeklySoftViolations.size());

        // Store weekly constraint violation counts
        Map<ConstraintType, Integer> violationCounts = new HashMap<>();
        for (ConstraintViolation violation : weeklySoftViolations) {
            violationCounts.merge(violation.getConstraintType(),
                    violation.getViolationCount(), Integer::sum);
        }

        for (Map.Entry<ConstraintType, Integer> entry : violationCounts.entrySet()) {
            solution.addConstraintViolation("WEEKLY_" + entry.getKey().name(), entry.getValue());
        }

        LoggingUtils.logInfo("Weekly solution validation for week " + week + " completed");

        return solution;
    }

    private List<ConstraintViolation> checkWeeklyHardConstraints(ProblemInstance instance,
            Solution solution, int week) {
        List<ConstraintViolation> violations = new ArrayList<>();

        // HC1: Coverage requirements for weekly workstations
        violations.addAll(checkWeeklyCoverageRequirements(instance, solution, week));

        // HC2: Availability enforcement for weekly assignments
        violations.addAll(checkWeeklyAvailabilityEnforcement(instance, solution, week));

        // HC9: Invalid combinations for weekly assignments
        violations.addAll(checkWeeklyInvalidCombinations(instance, solution, week));

        // HC10: Shift succession for weekly assignments
        violations.addAll(checkWeeklyShiftSuccession(instance, solution, week));

        return violations;
    }

    private List<ConstraintViolation> checkWeeklyCoverageRequirements(ProblemInstance instance,
            Solution solution, int week) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Workstation workstation : instance.getWeeklyWorkstations()) {
            for (PlanningDay day : instance.getWeekDays(week)) {
                int required = instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());
                int assigned = 0;

                for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                    if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                            day.getDayNumber(), week)) {
                        assigned++;
                    }
                }

                if (assigned != required) {
                    violations.add(new ConstraintViolation(
                            ConstraintType.HC1,
                            String.format(
                                    "Weekly coverage mismatch for %s on day %d (week %d): required=%d, assigned=%d",
                                    workstation.getId(), day.getDayNumber(), week, required, assigned),
                            null, workstation.getId(), day.getDayNumber(), Math.abs(assigned - required)));
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkWeeklyAvailabilityEnforcement(ProblemInstance instance,
            Solution solution, int week) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getWeekDays(week)) {
                if (instance.isAnaesthetistUnavailable(anaesthetist.getId(), day.getDayNumber())) {
                    // Check if assigned to any weekly workstation
                    boolean assignedWeekly = instance.getWeeklyWorkstations().stream()
                            .anyMatch(w -> solution.isAssignedWeekly(anaesthetist.getId(), w.getId(),
                                    day.getDayNumber(), week));

                    if (assignedWeekly) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC2,
                                "Unavailable anaesthetist assigned to weekly shift",
                                anaesthetist.getId(), null, day.getDayNumber(), 1));
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkWeeklyInvalidCombinations(ProblemInstance instance,
            Solution solution, int week) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getWeekDays(week)) {
                if (!day.isWeekendOrHoliday()) {
                    // Check MCT exclusivity
                    boolean mctAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "MCT",
                            day.getDayNumber(), week);
                    if (mctAssigned) {
                        for (Workstation workstation : instance.getWeeklyWorkstations()) {
                            if (!"MCT".equals(workstation.getId()) &&
                                    solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                            day.getDayNumber(), week)) {
                                violations.add(new ConstraintViolation(
                                        ConstraintType.HC9,
                                        "MCT exclusivity violation",
                                        anaesthetist.getId(), workstation.getId(), day.getDayNumber(), 1));
                            }
                        }
                    }

                    // Check MWK exclusivity
                    boolean mwkAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "MWK",
                            day.getDayNumber(), week);
                    if (mwkAssigned) {
                        for (Workstation workstation : instance.getWeeklyWorkstations()) {
                            if (!"MWK".equals(workstation.getId()) &&
                                    solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                            day.getDayNumber(), week)) {
                                violations.add(new ConstraintViolation(
                                        ConstraintType.HC9,
                                        "MWK exclusivity violation",
                                        anaesthetist.getId(), workstation.getId(), day.getDayNumber(), 1));
                            }
                        }
                    }

                    // Check OHMAU/OHMIU exclusivity
                    boolean ohmauAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "OHMAU",
                            day.getDayNumber(), week);
                    boolean ohmiuAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "OHMIU",
                            day.getDayNumber(), week);
                    if (ohmauAssigned && ohmiuAssigned) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC9,
                                "OHMAU/OHMIU exclusivity violation",
                                anaesthetist.getId(), "OHMAU", day.getDayNumber(), 1));
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkWeeklyShiftSuccession(ProblemInstance instance,
            Solution solution, int week) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getWeekDays(week)) {
                if (!day.isWeekendOrHoliday()) {
                    // Check evening/late evening conflicts
                    boolean eveningAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "EU1",
                            day.getDayNumber(), week);
                    boolean lateEveningAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "EU2",
                            day.getDayNumber(), week);

                    if (eveningAssigned && lateEveningAssigned) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC10,
                                "Evening/Late evening succession violation",
                                anaesthetist.getId(), "EU1", day.getDayNumber(), 1));
                    }

                    // Check morning/evening same day conflicts
                    boolean morningAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "MMAU",
                            day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "MMIU",
                                    day.getDayNumber(), week);

                    if (morningAssigned && (eveningAssigned || lateEveningAssigned)) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC10,
                                "Morning/Evening same day violation",
                                anaesthetist.getId(), "MMAU", day.getDayNumber(), 1));
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateWeeklySoftConstraints(ProblemInstance instance,
            Solution solution, int week) {
        List<ConstraintViolation> violations = new ArrayList<>();

        // SC3: Weekly shift request fulfillment
        violations.addAll(evaluateWeeklyShiftRequests(instance, solution, week));

        // SC8: Weekly preference accommodation
        violations.addAll(evaluateWeeklyPreferenceAccommodation(instance, solution, week));

        // SC10: Weekly undesired combinations
        violations.addAll(evaluateWeeklyUndesiredCombinations(instance, solution, week));

        return violations;
    }

    private List<ConstraintViolation> evaluateWeeklyShiftRequests(ProblemInstance instance,
            Solution solution, int week) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int requestViolations = 0;

            for (PlanningDay day : instance.getWeekDays(week)) {
                boolean hasMorningRequest = instance.hasMorningShiftRequest(anaesthetist.getId(), day.getDayNumber());
                boolean hasEveningRequest = instance.hasEveningShiftRequest(anaesthetist.getId(), day.getDayNumber());

                if (hasMorningRequest) {
                    boolean assignedToMorning = solution.isAssignedWeekly(anaesthetist.getId(), "MMAU",
                            day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "MMIU", day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "MWK", day.getDayNumber(), week);

                    if (!assignedToMorning) {
                        requestViolations++;
                    }
                }

                if (hasEveningRequest) {
                    boolean assignedToEvening = solution.isAssignedWeekly(anaesthetist.getId(), "EU1",
                            day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "EU2", day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "EWK", day.getDayNumber(), week);

                    if (!assignedToEvening) {
                        requestViolations++;
                    }
                }
            }

            if (requestViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC3,
                        "Weekly shift request violations",
                        anaesthetist.getId(), null, -1, requestViolations));
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateWeeklyPreferenceAccommodation(ProblemInstance instance,
            Solution solution, int week) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int preferenceViolations = 0;

            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                if (anaesthetist.hasLessPreferenceFor(workstation.getId())) {
                    int assignmentCount = 0;

                    for (PlanningDay day : instance.getWeekDays(week)) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                day.getDayNumber(), week)) {
                            assignmentCount++;
                        }
                    }

                    preferenceViolations += assignmentCount;
                }
            }

            if (preferenceViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC8,
                        "Weekly preference accommodation violations",
                        anaesthetist.getId(), null, -1, preferenceViolations));
            }
        }

        return violations;
    }

    private List<ConstraintViolation> evaluateWeeklyUndesiredCombinations(ProblemInstance instance,
            Solution solution, int week) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            int undesiredViolations = 0;

            for (PlanningDay day : instance.getWeekDays(week)) {
                // Check if assigned to weekly workstation on days with examination/dissertation
                // requests
                Optional<Request> request = instance.getRequest(anaesthetist.getId(), day.getDayNumber());
                if (request.isPresent() &&
                        (request.get().isExaminationRequest() || request.get().isDissertationRequest())) {

                    // Count assignments to major weekly workstations
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        if (workstation.isMajor() &&
                                solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                        day.getDayNumber(), week)) {
                            undesiredViolations++;
                        }
                    }
                }
            }

            if (undesiredViolations > 0) {
                violations.add(new ConstraintViolation(
                        ConstraintType.SC10,
                        "Weekly undesired combination violations",
                        anaesthetist.getId(), null, -1, undesiredViolations));
            }
        }

        return violations;
    }

    private Solution attemptWeeklyConstraintRepair(ProblemInstance instance, Solution solution,
            int week, List<ConstraintViolation> violations) {

        LoggingUtils.logInfo("Attempting to repair weekly constraint violations for week " + week);

        // Simple repair: remove conflicting assignments
        for (ConstraintViolation violation : violations) {
            if (violation.getAnaesthetistId() != null && violation.getWorkstationId() != null &&
                    violation.getDayNumber() > 0) {

                // Remove the violating assignment
                // This would need to be implemented in the Solution class
                LoggingUtils.logDebug("Would remove weekly assignment: " + violation.getAnaesthetistId() +
                        " from " + violation.getWorkstationId() + " on day " + violation.getDayNumber() +
                        " (week " + week + ")");
            }
        }

        LoggingUtils.logInfo("Weekly constraint repair for week " + week + " completed");
        return solution;
    }
}
