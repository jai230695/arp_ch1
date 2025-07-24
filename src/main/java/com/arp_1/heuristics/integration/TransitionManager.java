// File: src/main/java/com/arp/heuristics/integration/TransitionManager.java
package com.arp_1.heuristics.integration;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Manages 3-day transitions between monthly and weekly rosters
 */
public class TransitionManager {

    public Map<String, List<String>> handle3DayTransitions(Solution currentSolution, int week,
            ProblemInstance instance) {

        LoggingUtils.logInfo("Handling 3-day transitions for week " + week);

        Map<String, List<String>> transitionConstraints = new HashMap<>();

        // Handle transition from previous month (for week 1)
        if (week == 1) {
            transitionConstraints.putAll(handlePreviousMonthTransitions(instance));
        }

        // Handle transitions from previous weeks in current month
        if (week > 1) {
            transitionConstraints.putAll(handlePreviousWeekTransitions(currentSolution, week, instance));
        }

        // Handle transitions to next week (for weeks 1-3)
        if (week < 4) {
            Map<String, List<String>> nextWeekConstraints = prepareNextWeekTransitions(currentSolution, week, instance);

            // Merge with existing constraints
            for (Map.Entry<String, List<String>> entry : nextWeekConstraints.entrySet()) {
                transitionConstraints.merge(entry.getKey(), entry.getValue(),
                        (existing, newList) -> {
                            List<String> merged = new ArrayList<>(existing);
                            merged.addAll(newList);
                            return merged;
                        });
            }
        }

        LoggingUtils.logInfo("3-day transitions prepared: " + transitionConstraints.size() +
                " anaesthetists affected");

        return transitionConstraints;
    }

    private Map<String, List<String>> handlePreviousMonthTransitions(ProblemInstance instance) {
        Map<String, List<String>> constraints = new HashMap<>();

        // Use previous month assignments to determine transition constraints
        for (Assignment assignment : instance.getPreviousMonthAssignments()) {
            // Check if assignment is in the last 3 days of previous month
            if (assignment.getDayNumber() >= 26) { // Days 26, 27, 28 of previous month

                String anaesthetistId = assignment.getAnaesthetistId();
                String workstationId = assignment.getWorkstationId();

                // Create transition constraint based on assignment type
                List<String> transitionRules = createTransitionRules(workstationId,
                        assignment.getDayNumber(), instance);

                if (!transitionRules.isEmpty()) {
                    constraints.put(anaesthetistId, transitionRules);
                }
            }
        }

        LoggingUtils.logInfo("Previous month transitions: " + constraints.size() + " constraints created");
        return constraints;
    }

    private Map<String, List<String>> handlePreviousWeekTransitions(Solution currentSolution,
            int week, ProblemInstance instance) {
        Map<String, List<String>> constraints = new HashMap<>();

        // Look at assignments from previous week (last 3 days)
        int previousWeek = week - 1;
        List<PlanningDay> previousWeekDays = instance.getWeekDays(previousWeek);

        // Focus on last 3 days of previous week
        List<PlanningDay> transitionDays = previousWeekDays.subList(
                Math.max(0, previousWeekDays.size() - 3), previousWeekDays.size());

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            List<String> anaesthetistConstraints = new ArrayList<>();

            for (PlanningDay day : transitionDays) {
                // Check monthly assignments
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    if (currentSolution.isAssignedMonthly(anaesthetist.getId(),
                            workstation.getId(), day.getDayNumber())) {

                        List<String> rules = createTransitionRules(workstation.getId(),
                                day.getDayNumber(), instance);
                        anaesthetistConstraints.addAll(rules);
                    }
                }

                // Check weekly assignments from previous week
                for (Workstation workstation : instance.getWeeklyWorkstations()) {
                    if (currentSolution.isAssignedWeekly(anaesthetist.getId(),
                            workstation.getId(), day.getDayNumber(), previousWeek)) {

                        List<String> rules = createWeeklyTransitionRules(workstation.getId(),
                                day.getDayNumber(), week, instance);
                        anaesthetistConstraints.addAll(rules);
                    }
                }
            }

            if (!anaesthetistConstraints.isEmpty()) {
                constraints.put(anaesthetist.getId(), anaesthetistConstraints);
            }
        }

        LoggingUtils.logInfo("Previous week transitions: " + constraints.size() + " constraints created");
        return constraints;
    }

    private Map<String, List<String>> prepareNextWeekTransitions(Solution currentSolution,
            int week, ProblemInstance instance) {
        Map<String, List<String>> constraints = new HashMap<>();

        // This is for forward planning - ensuring current week assignments
        // are compatible with next week requirements

        List<PlanningDay> currentWeekDays = instance.getWeekDays(week);

        // Focus on last 3 days of current week for next week transition
        List<PlanningDay> transitionDays = currentWeekDays.subList(
                Math.max(0, currentWeekDays.size() - 3), currentWeekDays.size());

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            List<String> anaesthetistConstraints = new ArrayList<>();

            for (PlanningDay day : transitionDays) {
                // Check what monthly assignments are planned for these days
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    if (currentSolution.isAssignedMonthly(anaesthetist.getId(),
                            workstation.getId(), day.getDayNumber())) {

                        // Create forward transition rules
                        List<String> rules = createForwardTransitionRules(workstation.getId(),
                                day.getDayNumber(), week + 1, instance);
                        anaesthetistConstraints.addAll(rules);
                    }
                }
            }

            if (!anaesthetistConstraints.isEmpty()) {
                constraints.put(anaesthetist.getId(), anaesthetistConstraints);
            }
        }

        LoggingUtils.logInfo("Next week transition preparation: " + constraints.size() + " constraints created");
        return constraints;
    }

    private List<String> createTransitionRules(String workstationId, int dayNumber,
            ProblemInstance instance) {
        List<String> rules = new ArrayList<>();

        switch (workstationId) {
            case "SGOT":
                // SGOT requires rest day after assignment
                if (dayNumber < 28) {
                    rules.add("REST_DAY_AFTER_SGOT:" + (dayNumber + 1));
                }
                // Avoid consecutive SGOT assignments
                rules.add("AVOID_CONSECUTIVE_SGOT:" + dayNumber);
                break;

            case "CICU":
                // CICU prefers consecutive assignments
                rules.add("PREFER_CONSECUTIVE_CICU:" + dayNumber);
                break;

            case "CGOT":
            case "PWOT":
                // Standard on-call transitions
                rules.add("STANDARD_ONCALL_TRANSITION:" + workstationId + ":" + dayNumber);
                break;

            case "SICU":
                // SICU weekend pairing requirements
                PlanningDay day = instance.getPlanningDay(dayNumber);
                if (day != null && day.isWeekendOrHoliday()) {
                    rules.add("WEEKEND_PAIRING_SICU:" + dayNumber);
                }
                break;
        }

        return rules;
    }

    private List<String> createWeeklyTransitionRules(String workstationId, int dayNumber,
            int nextWeek, ProblemInstance instance) {
        List<String> rules = new ArrayList<>();

        switch (workstationId) {
            case "MCT":
                // Cardiothoracic work - avoid overloading
                rules.add("LIMIT_MCT_CONSECUTIVE:" + dayNumber + ":" + nextWeek);
                break;

            case "EU1":
            case "EU2":
                // Evening work - ensure adequate rest
                rules.add("EVENING_REST_TRANSITION:" + workstationId + ":" + dayNumber);
                break;

            case "MWK":
                // Morning work - balance with evening assignments
                rules.add("BALANCE_MORNING_WORK:" + dayNumber + ":" + nextWeek);
                break;

            case "OHMAU":
            case "OHMIU":
                // Office hours - weekend considerations
                PlanningDay day = instance.getPlanningDay(dayNumber);
                if (day != null && day.isWeekendOrHoliday()) {
                    rules.add("WEEKEND_OFFICE_HOURS_TRANSITION:" + workstationId + ":" + dayNumber);
                }
                break;
        }

        return rules;
    }

    private List<String> createForwardTransitionRules(String workstationId, int dayNumber,
            int nextWeek, ProblemInstance instance) {
        List<String> rules = new ArrayList<>();

        // Rules for ensuring current assignments are compatible with next week planning
        switch (workstationId) {
            case "SGOT":
                // If SGOT assigned late in week, consider impact on next week
                rules.add("FORWARD_SGOT_IMPACT:" + dayNumber + ":" + nextWeek);
                break;

            case "CICU":
                // CICU consecutive patterns should be considered for next week
                rules.add("FORWARD_CICU_PATTERN:" + dayNumber + ":" + nextWeek);
                break;

            default:
                // General forward planning rule
                rules.add("FORWARD_PLANNING:" + workstationId + ":" + dayNumber + ":" + nextWeek);
                break;
        }

        return rules;
    }

    /**
     * Evaluate if a proposed weekly assignment violates transition constraints
     */
    public boolean violatesTransitionConstraints(String anaesthetistId, String workstationId,
            int dayNumber, int week,
            Map<String, List<String>> transitionConstraints) {

        List<String> constraints = transitionConstraints.get(anaesthetistId);
        if (constraints == null || constraints.isEmpty()) {
            return false;
        }

        for (String constraint : constraints) {
            if (evaluateConstraintViolation(constraint, workstationId, dayNumber, week)) {
                LoggingUtils.logDebug("Transition constraint violation: " + constraint +
                        " for assignment " + anaesthetistId + " to " + workstationId +
                        " on day " + dayNumber + " (week " + week + ")");
                return true;
            }
        }

        return false;
    }

    private boolean evaluateConstraintViolation(String constraint, String workstationId,
            int dayNumber, int week) {

        String[] parts = constraint.split(":");
        if (parts.length < 2)
            return false;

        String constraintType = parts[0];

        switch (constraintType) {
            case "REST_DAY_AFTER_SGOT":
                // Cannot assign on specified rest day
                int restDay = Integer.parseInt(parts[1]);
                return dayNumber == restDay;

            case "AVOID_CONSECUTIVE_SGOT":
                // Avoid assignments that could lead to consecutive SGOT
                return "SGOT".equals(workstationId);

            case "LIMIT_MCT_CONSECUTIVE":
                // Limit consecutive MCT assignments
                return "MCT".equals(workstationId) && parts.length > 2 &&
                        week == Integer.parseInt(parts[2]);

            case "EVENING_REST_TRANSITION":
                // Ensure rest after evening work
                return workstationId.startsWith("EU") || workstationId.equals("EWK");

            case "WEEKEND_PAIRING_SICU":
                // Weekend pairing requirements for SICU
                return "SICU".equals(workstationId);

            default:
                // Default: no violation for unknown constraints
                return false;
        }
    }

    /**
     * Get transition bonus for assignments that help with transitions
     */
    public double calculateTransitionBonus(String anaesthetistId, String workstationId,
            int dayNumber, int week,
            Map<String, List<String>> transitionConstraints) {

        double bonus = 0.0;
        List<String> constraints = transitionConstraints.get(anaesthetistId);

        if (constraints == null || constraints.isEmpty()) {
            return bonus;
        }

        for (String constraint : constraints) {
            bonus += evaluateTransitionBonus(constraint, workstationId, dayNumber, week);
        }

        return bonus;
    }

    private double evaluateTransitionBonus(String constraint, String workstationId,
            int dayNumber, int week) {

        String[] parts = constraint.split(":");
        if (parts.length < 2)
            return 0.0;

        String constraintType = parts[0];

        switch (constraintType) {
            case "PREFER_CONSECUTIVE_CICU":
                // Bonus for CICU consecutive assignments
                return "CICU".equals(workstationId) ? 20.0 : 0.0;

            case "BALANCE_MORNING_WORK":
                // Bonus for balancing morning work
                return workstationId.contains("M") ? 10.0 : 0.0;

            case "STANDARD_ONCALL_TRANSITION":
                // Small bonus for standard transitions
                return 5.0;

            default:
                return 0.0;
        }
    }
}
