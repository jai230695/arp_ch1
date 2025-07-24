// File: src/main/java/com/arp/core/constraints/HardConstraintChecker.java
package com.arp_1.core.constraints;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import java.util.*;

public class HardConstraintChecker {

    public List<ConstraintViolation> checkAllHardConstraints(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        // Check each hard constraint
        violations.addAll(checkHC1_CoverageRequirements(solution, instance));
        violations.addAll(checkHC2_AvailabilityEnforcement(solution, instance));
        violations.addAll(checkHC3_SGOTConsecutiveRestrictions(solution, instance));
        violations.addAll(checkHC4_QualificationRequirements(solution, instance));
        violations.addAll(checkHC5_WeeklyWorkingHours(solution, instance));
        violations.addAll(checkHC6_RestDayAfterSGOT(solution, instance));
        violations.addAll(checkHC7_WeekendPairing(solution, instance));
        violations.addAll(checkHC8_DailyWorkloadLimits(solution, instance));
        violations.addAll(checkHC9_InvalidCombinations(solution, instance));
        violations.addAll(checkHC10_ShiftSuccession(solution, instance));
        violations.addAll(checkHC11_MandatoryPairings(solution, instance));

        return violations;
    }

    private List<ConstraintViolation> checkHC1_CoverageRequirements(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        // Check monthly workstations
        for (Workstation workstation : instance.getMonthlyWorkstations()) {
            for (PlanningDay day : instance.getPlanningDays()) {
                int required = instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());
                int assigned = 0;

                for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                    if (solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), day.getDayNumber())) {
                        assigned++;
                    }
                }

                if (assigned != required) {
                    violations.add(new ConstraintViolation(
                            ConstraintType.HC1,
                            String.format("Coverage mismatch for %s on day %d: required=%d, assigned=%d",
                                    workstation.getId(), day.getDayNumber(), required, assigned),
                            null, workstation.getId(), day.getDayNumber(), Math.abs(assigned - required)));
                }
            }
        }

        // Check weekly workstations
        for (Workstation workstation : instance.getWeeklyWorkstations()) {
            for (int week = 1; week <= 4; week++) {
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
        }

        return violations;
    }

    private List<ConstraintViolation> checkHC2_AvailabilityEnforcement(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getPlanningDays()) {
                if (instance.isAnaesthetistUnavailable(anaesthetist.getId(), day.getDayNumber())) {
                    // Check if assigned to any monthly workstation
                    boolean assignedMonthly = instance.getMonthlyWorkstations().stream()
                            .anyMatch(w -> solution.isAssignedMonthly(anaesthetist.getId(), w.getId(),
                                    day.getDayNumber()));

                    if (assignedMonthly) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC2,
                                "Unavailable anaesthetist assigned to monthly shift",
                                anaesthetist.getId(), null, day.getDayNumber(), 1));
                    }

                    // Check if assigned to any weekly workstation
                    int week = day.getWeek();
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

    private List<ConstraintViolation> checkHC3_SGOTConsecutiveRestrictions(Solution solution,
            ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getPlanningDays()) {
                if (!day.isWeekendOrHoliday() && day.getDayNumber() < 28) {
                    // Check consecutive weekday SGOT assignments
                    boolean currentDay = solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", day.getDayNumber());
                    boolean nextDay = solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", day.getDayNumber() + 1);

                    PlanningDay nextPlanningDay = instance.getPlanningDay(day.getDayNumber() + 1);
                    if (currentDay && nextDay && (nextPlanningDay == null || !nextPlanningDay.isWeekendOrHoliday())) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC3,
                                "Consecutive weekday SGOT assignments",
                                anaesthetist.getId(), "SGOT", day.getDayNumber(), 1));
                    }
                }
            }

            // Check CGOT consecutive restrictions as well (from MILP model)
            for (PlanningDay day : instance.getPlanningDays()) {
                if (!day.isWeekendOrHoliday() && day.getDayNumber() < 28) {
                    boolean currentDay = solution.isAssignedMonthly(anaesthetist.getId(), "CGOT", day.getDayNumber());
                    boolean nextDay = solution.isAssignedMonthly(anaesthetist.getId(), "CGOT", day.getDayNumber() + 1);

                    PlanningDay nextPlanningDay = instance.getPlanningDay(day.getDayNumber() + 1);
                    if (currentDay && nextDay && (nextPlanningDay == null || !nextPlanningDay.isWeekendOrHoliday())) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC3,
                                "Consecutive weekday CGOT assignments",
                                anaesthetist.getId(), "CGOT", day.getDayNumber(), 1));
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkHC4_QualificationRequirements(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            // Check monthly assignments
            for (Workstation workstation : instance.getMonthlyWorkstations()) {
                for (PlanningDay day : instance.getPlanningDays()) {
                    if (solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), day.getDayNumber())) {
                        if (!anaesthetist.isQualifiedFor(workstation.getId())) {
                            violations.add(new ConstraintViolation(
                                    ConstraintType.HC4,
                                    "Unqualified anaesthetist assigned",
                                    anaesthetist.getId(), workstation.getId(), day.getDayNumber(), 1));
                        }

                        // Special SICU qualification check for weekdays only (from MILP model)
                        if ("SICU".equals(workstation.getId()) && !day.isWeekendOrHoliday()) {
                            if (!anaesthetist.isQualifiedFor("SICU")) {
                                violations.add(new ConstraintViolation(
                                        ConstraintType.HC4,
                                        "Unqualified anaesthetist assigned to SICU on weekday",
                                        anaesthetist.getId(), workstation.getId(), day.getDayNumber(), 1));
                            }
                        }
                    }
                }
            }

            // Check weekly assignments
            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                for (int week = 1; week <= 4; week++) {
                    for (PlanningDay day : instance.getWeekDays(week)) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                day.getDayNumber(), week)) {
                            if (!anaesthetist.isQualifiedFor(workstation.getId())) {
                                violations.add(new ConstraintViolation(
                                        ConstraintType.HC4,
                                        "Unqualified anaesthetist assigned to weekly shift",
                                        anaesthetist.getId(), workstation.getId(), day.getDayNumber(), 1));
                            }

                            // Special OHMAU qualification check for weekdays only
                            if ("OHMAU".equals(workstation.getId()) && !day.isWeekendOrHoliday()) {
                                if (!anaesthetist.isQualifiedFor("OHMAU")) {
                                    violations.add(new ConstraintViolation(
                                            ConstraintType.HC4,
                                            "Unqualified anaesthetist assigned to OHMAU on weekday",
                                            anaesthetist.getId(), workstation.getId(), day.getDayNumber(), 1));
                                }
                            }
                        }
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkHC5_WeeklyWorkingHours(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (int week = 1; week <= 4; week++) {
                // Check specific workstations that have hour limits (CGOT, SGOT, PWOT, SICU)
                String[] hourLimitedWorkstations = { "CGOT", "SGOT", "PWOT", "SICU" };

                for (String workstationId : hourLimitedWorkstations) {
                    Workstation workstation = instance.getWorkstationById(workstationId);
                    if (workstation != null) {
                        int weeklyHours = 0;

                        for (PlanningDay day : instance.getWeekDays(week)) {
                            if (solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(),
                                    day.getDayNumber())) {
                                int duration = workstation.getEndTime() - workstation.getStartTime();
                                weeklyHours += duration;
                            }
                        }

                        if (weeklyHours > workstation.getMaxWorkingHour()) {
                            violations.add(new ConstraintViolation(
                                    ConstraintType.HC5,
                                    String.format("Weekly working hours exceeded for %s: %d > %d",
                                            workstationId, weeklyHours, workstation.getMaxWorkingHour()),
                                    anaesthetist.getId(), workstation.getId(), -1,
                                    weeklyHours - workstation.getMaxWorkingHour()));
                        }
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkHC6_RestDayAfterSGOT(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getPlanningDays()) {
                if (solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", day.getDayNumber())) {
                    int nextDay = day.getDayNumber() + 1;

                    if (nextDay <= 28) {
                        // Check if assigned to any monthly workstation on the next day
                        boolean assignedNextDayMonthly = false;
                        for (Workstation workstation : instance.getMonthlyWorkstations()) {
                            if (!workstation.getId().equals("SGOT") &&
                                    solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), nextDay)) {
                                assignedNextDayMonthly = true;
                                break;
                            }
                        }

                        if (assignedNextDayMonthly) {
                            violations.add(new ConstraintViolation(
                                    ConstraintType.HC6,
                                    "No rest day after SGOT assignment - assigned to monthly workstation",
                                    anaesthetist.getId(), "SGOT", day.getDayNumber(), 1));
                        }

                        // Check if assigned to any weekly workstation on the next day
                        PlanningDay nextPlanningDay = instance.getPlanningDay(nextDay);
                        if (nextPlanningDay != null) {
                            int nextWeek = nextPlanningDay.getWeek();
                            boolean assignedNextDayWeekly = false;
                            for (Workstation workstation : instance.getWeeklyWorkstations()) {
                                if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(), nextDay,
                                        nextWeek)) {
                                    assignedNextDayWeekly = true;
                                    break;
                                }
                            }

                            if (assignedNextDayWeekly) {
                                violations.add(new ConstraintViolation(
                                        ConstraintType.HC6,
                                        "No rest day after SGOT assignment - assigned to weekly workstation",
                                        anaesthetist.getId(), "SGOT", day.getDayNumber(), 1));
                            }
                        }
                    }

                    // Check conflicts with next day requests (D, CME, XM, CT)
                    if (nextDay <= 28) {
                        Optional<Request> nextDayRequest = instance.getRequest(anaesthetist.getId(), nextDay);
                        if (nextDayRequest.isPresent()) {
                            Request request = nextDayRequest.get();
                            if (request.isDissertationRequest() || request.isTeachingRequest() ||
                                    request.isExaminationRequest() || request.isCardiothoracicRequest()) {
                                violations.add(new ConstraintViolation(
                                        ConstraintType.HC6,
                                        String.format("SGOT assignment conflicts with next day %s request",
                                                request.getRequestType()),
                                        anaesthetist.getId(), "SGOT", day.getDayNumber(), 1));
                            }
                        }
                    }
                }
            }

            // Check weekend SGOT to next weekday transitions
            for (WeekendPair weekendPair : instance.getWeekendPairs()) {
                if (solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", weekendPair.getDay2())) {
                    int nextDay = weekendPair.getDay2() + 1;
                    if (nextDay <= 28) {
                        boolean assignedNextDay = false;
                        for (Workstation workstation : instance.getMonthlyWorkstations()) {
                            if (!workstation.getId().equals("SGOT") &&
                                    solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), nextDay)) {
                                assignedNextDay = true;
                                break;
                            }
                        }

                        if (assignedNextDay) {
                            violations.add(new ConstraintViolation(
                                    ConstraintType.HC6,
                                    "No rest day after weekend SGOT assignment",
                                    anaesthetist.getId(), "SGOT", weekendPair.getDay2(), 1));
                        }
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkHC7_WeekendPairing(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (WeekendPair pair : instance.getWeekendPairs()) {
            for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                // Check CGOT pairing requirement
                boolean cgotDay1 = solution.isAssignedMonthly(anaesthetist.getId(), "CGOT", pair.getDay1());
                boolean cgotDay2 = solution.isAssignedMonthly(anaesthetist.getId(), "CGOT", pair.getDay2());

                if (cgotDay1 != cgotDay2) {
                    violations.add(new ConstraintViolation(
                            ConstraintType.HC7,
                            String.format("CGOT weekend pairing violation on days %d-%d",
                                    pair.getDay1(), pair.getDay2()),
                            anaesthetist.getId(), "CGOT", pair.getDay1(), 1));
                }

                // Check SGOT and SICU combined pairing requirement
                boolean sgotDay1 = solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", pair.getDay1());
                boolean sgotDay2 = solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", pair.getDay2());
                boolean sicuDay1 = solution.isAssignedMonthly(anaesthetist.getId(), "SICU", pair.getDay1());
                boolean sicuDay2 = solution.isAssignedMonthly(anaesthetist.getId(), "SICU", pair.getDay2());

                // SGOT must be paired
                if (sgotDay1 != sgotDay2) {
                    violations.add(new ConstraintViolation(
                            ConstraintType.HC7,
                            String.format("SGOT weekend pairing violation on days %d-%d",
                                    pair.getDay1(), pair.getDay2()),
                            anaesthetist.getId(), "SGOT", pair.getDay1(), 1));
                }

                // SICU must match SGOT assignment
                if (sgotDay1 && !sicuDay1) {
                    violations.add(new ConstraintViolation(
                            ConstraintType.HC7,
                            String.format("SICU must be assigned with SGOT on day %d", pair.getDay1()),
                            anaesthetist.getId(), "SICU", pair.getDay1(), 1));
                }
                if (sgotDay2 && !sicuDay2) {
                    violations.add(new ConstraintViolation(
                            ConstraintType.HC7,
                            String.format("SICU must be assigned with SGOT on day %d", pair.getDay2()),
                            anaesthetist.getId(), "SICU", pair.getDay2(), 1));
                }

                // Check OHMAU weekend pairing for weekly roster
                for (int week = 1; week <= 4; week++) {
                    PlanningDay day1 = instance.getPlanningDay(pair.getDay1());
                    PlanningDay day2 = instance.getPlanningDay(pair.getDay2());

                    if (day1 != null && day2 != null && day1.getWeek() == week && day2.getWeek() == week) {
                        boolean ohmaDay1 = solution.isAssignedWeekly(anaesthetist.getId(), "OHMAU", pair.getDay1(),
                                week);
                        boolean ohmaDay2 = solution.isAssignedWeekly(anaesthetist.getId(), "OHMAU", pair.getDay2(),
                                week);

                        if (ohmaDay1 != ohmaDay2) {
                            violations.add(new ConstraintViolation(
                                    ConstraintType.HC7,
                                    String.format("OHMAU weekend pairing violation on days %d-%d (week %d)",
                                            pair.getDay1(), pair.getDay2(), week),
                                    anaesthetist.getId(), "OHMAU", pair.getDay1(), 1));
                        }
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkHC8_DailyWorkloadLimits(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getPlanningDays()) {
                if (!day.isWeekendOrHoliday()) {
                    double totalWorkload = 0.0;

                    // Calculate monthly workload
                    for (Workstation workstation : instance.getMonthlyWorkstations()) {
                        if (solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), day.getDayNumber())) {
                            totalWorkload += workstation.getWeight();
                        }
                    }

                    // Calculate weekly workload
                    int week = day.getWeek();
                    for (Workstation workstation : instance.getWeeklyWorkstations()) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                day.getDayNumber(), week)) {
                            totalWorkload += workstation.getWeight();
                        }
                    }

                    if (totalWorkload > instance.getMaxDailyWorkload()) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC8,
                                String.format("Daily workload limit exceeded: %.1f > %.1f",
                                        totalWorkload, instance.getMaxDailyWorkload()),
                                anaesthetist.getId(), null, day.getDayNumber(),
                                (int) Math.ceil(totalWorkload - instance.getMaxDailyWorkload())));
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkHC9_InvalidCombinations(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getPlanningDays()) {
                // Check examination request conflicts with monthly workstations
                Optional<Request> request = instance.getRequest(anaesthetist.getId(), day.getDayNumber());
                if (request.isPresent() && request.get().isExaminationRequest()) {
                    String[] conflictingWorkstations = { "CGOT", "SGOT", "PWOT", "CICU", "SICU" };
                    for (String workstationId : conflictingWorkstations) {
                        if (solution.isAssignedMonthly(anaesthetist.getId(), workstationId, day.getDayNumber())) {
                            violations.add(new ConstraintViolation(
                                    ConstraintType.HC9,
                                    String.format("Examination request conflicts with %s assignment", workstationId),
                                    anaesthetist.getId(), workstationId, day.getDayNumber(), 1));
                        }
                    }
                }

                // Check SGOT + SICU combination (not allowed on weekdays)
                if (!day.isWeekendOrHoliday()) {
                    boolean sgotAssigned = solution.isAssignedMonthly(anaesthetist.getId(), "SGOT", day.getDayNumber());
                    boolean sicuAssigned = solution.isAssignedMonthly(anaesthetist.getId(), "SICU", day.getDayNumber());

                    if (sgotAssigned && sicuAssigned) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC9,
                                "Invalid SGOT + SICU combination on weekday",
                                anaesthetist.getId(), "SGOT", day.getDayNumber(), 1));
                    }
                }

                // Check weekly location exclusivity rules
                int week = day.getWeek();
                if (!day.isWeekendOrHoliday()) {
                    // MWK exclusivity - if assigned to MWK, cannot be assigned to other weekly
                    // locations
                    boolean mwkAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "MWK", day.getDayNumber(),
                            week);
                    if (mwkAssigned) {
                        for (Workstation workstation : instance.getWeeklyWorkstations()) {
                            if (!workstation.getId().equals("MWK") &&
                                    solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                            day.getDayNumber(), week)) {
                                violations.add(new ConstraintViolation(
                                        ConstraintType.HC9,
                                        String.format("MWK exclusivity violation with %s", workstation.getId()),
                                        anaesthetist.getId(), workstation.getId(), day.getDayNumber(), 1));
                            }
                        }
                    }

                    // MCT exclusivity - if assigned to MCT, cannot be assigned to other weekly
                    // locations
                    boolean mctAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "MCT", day.getDayNumber(),
                            week);
                    if (mctAssigned) {
                        for (Workstation workstation : instance.getWeeklyWorkstations()) {
                            if (!workstation.getId().equals("MCT") &&
                                    solution.isAssignedWeekly(anaesthetist.getId(), workstation.getId(),
                                            day.getDayNumber(), week)) {
                                violations.add(new ConstraintViolation(
                                        ConstraintType.HC9,
                                        String.format("MCT exclusivity violation with %s", workstation.getId()),
                                        anaesthetist.getId(), workstation.getId(), day.getDayNumber(), 1));
                            }
                        }
                    }

                    // EWK and EU1/EU2 exclusivity
                    boolean ewkAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "EWK", day.getDayNumber(),
                            week);
                    if (ewkAssigned) {
                        if (solution.isAssignedWeekly(anaesthetist.getId(), "EU1", day.getDayNumber(), week) ||
                                solution.isAssignedWeekly(anaesthetist.getId(), "EU2", day.getDayNumber(), week)) {
                            violations.add(new ConstraintViolation(
                                    ConstraintType.HC9,
                                    "EWK cannot be combined with EU1 or EU2",
                                    anaesthetist.getId(), "EWK", day.getDayNumber(), 1));
                        }
                    }

                    // OHMAU and OHMIU exclusivity
                    boolean ohmauAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "OHMAU", day.getDayNumber(),
                            week);
                    boolean ohmiuAssigned = solution.isAssignedWeekly(anaesthetist.getId(), "OHMIU", day.getDayNumber(),
                            week);
                    if (ohmauAssigned && ohmiuAssigned) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC9,
                                "OHMAU and OHMIU cannot be combined",
                                anaesthetist.getId(), "OHMAU", day.getDayNumber(), 1));
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkHC10_ShiftSuccession(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (PlanningDay day : instance.getPlanningDays()) {
                if (!day.isWeekendOrHoliday()) {
                    int week = day.getWeek();

                    // Check monthly to weekly succession conflicts
                    boolean assignedToMonthly = false;
                    for (Workstation workstation : instance.getMonthlyWorkstations()) {
                        if (solution.isAssignedMonthly(anaesthetist.getId(), workstation.getId(), day.getDayNumber())) {
                            assignedToMonthly = true;
                            break;
                        }
                    }

                    if (assignedToMonthly) {
                        // Check if also assigned to evening or late evening weekly shifts
                        boolean assignedToEvening = solution.isAssignedWeekly(anaesthetist.getId(), "EU1",
                                day.getDayNumber(), week) ||
                                solution.isAssignedWeekly(anaesthetist.getId(), "EU2", day.getDayNumber(), week) ||
                                solution.isAssignedWeekly(anaesthetist.getId(), "EWK", day.getDayNumber(), week);

                        if (assignedToEvening) {
                            violations.add(new ConstraintViolation(
                                    ConstraintType.HC10,
                                    "Invalid succession: monthly assignment with evening shift",
                                    anaesthetist.getId(), null, day.getDayNumber(), 1));
                        }
                    }

                    // Check weekly shift succession conflicts
                    boolean assignedToEvening = solution.isAssignedWeekly(anaesthetist.getId(), "EU1",
                            day.getDayNumber(), week);
                    boolean assignedToLateEvening = solution.isAssignedWeekly(anaesthetist.getId(), "EU2",
                            day.getDayNumber(), week);
                    boolean assignedToMorning = solution.isAssignedWeekly(anaesthetist.getId(), "MMAU",
                            day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "MMIU", day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "MCT", day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "MWK", day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "MPMIS", day.getDayNumber(), week);
                    boolean assignedToOfficeHours = solution.isAssignedWeekly(anaesthetist.getId(), "OHMAU",
                            day.getDayNumber(), week) ||
                            solution.isAssignedWeekly(anaesthetist.getId(), "OHMIU", day.getDayNumber(), week);

                    // Evening and Late Evening cannot be combined
                    if (assignedToEvening && assignedToLateEvening) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC10,
                                "Evening and Late Evening shifts cannot be combined",
                                anaesthetist.getId(), "EU1", day.getDayNumber(), 1));
                    }

                    // Morning/Evening/Late Evening cannot be combined with Office Hours
                    if ((assignedToMorning || assignedToEvening || assignedToLateEvening) && assignedToOfficeHours) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC10,
                                "Morning/Evening shifts cannot be combined with Office Hours",
                                anaesthetist.getId(), null, day.getDayNumber(), 1));
                    }

                    // Morning cannot be combined with Evening/Late Evening
                    if (assignedToMorning && (assignedToEvening || assignedToLateEvening)) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC10,
                                "Morning shifts cannot be combined with Evening shifts",
                                anaesthetist.getId(), null, day.getDayNumber(), 1));
                    }
                }
            }
        }

        return violations;
    }

    private List<ConstraintViolation> checkHC11_MandatoryPairings(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = new ArrayList<>();

        // Check mandatory workstation pairs during weekend/holiday periods
        for (WeekendPair weekendPair : instance.getWeekendPairs()) {
            for (WorkstationPair workstationPair : instance.getWorkstationPairs()) {
                for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
                    // Check both days of the weekend pair
                    boolean ws1Day1 = solution.isAssignedMonthly(anaesthetist.getId(),
                            workstationPair.getWorkstation1(), weekendPair.getDay1());
                    boolean ws1Day2 = solution.isAssignedMonthly(anaesthetist.getId(),
                            workstationPair.getWorkstation1(), weekendPair.getDay2());
                    boolean ws2Day1 = solution.isAssignedMonthly(anaesthetist.getId(),
                            workstationPair.getWorkstation2(), weekendPair.getDay1());
                    boolean ws2Day2 = solution.isAssignedMonthly(anaesthetist.getId(),
                            workstationPair.getWorkstation2(), weekendPair.getDay2());

                    // If assigned to first workstation on weekend, must be assigned to second
                    // workstation
                    if ((ws1Day1 || ws1Day2) && !(ws2Day1 && ws2Day2)) {
                        violations.add(new ConstraintViolation(
                                ConstraintType.HC11,
                                String.format(
                                        "Mandatory pairing violation: %s assigned but %s not paired on weekend days %d-%d",
                                        workstationPair.getWorkstation1(), workstationPair.getWorkstation2(),
                                        weekendPair.getDay1(), weekendPair.getDay2()),
                                anaesthetist.getId(), workstationPair.getWorkstation1(), weekendPair.getDay1(), 1));
                    }

                    // Check weekly mandatory pairings
                    PlanningDay day1 = instance.getPlanningDay(weekendPair.getDay1());
                    PlanningDay day2 = instance.getPlanningDay(weekendPair.getDay2());

                    if (day1 != null && day2 != null && day1.getWeek() == day2.getWeek()) {
                        int week = day1.getWeek();

                        // Check if weekly workstation pairing is required
                        boolean weeklyWs1Day1 = solution.isAssignedWeekly(anaesthetist.getId(),
                                workstationPair.getWorkstation1(),
                                weekendPair.getDay1(), week);
                        boolean weeklyWs1Day2 = solution.isAssignedWeekly(anaesthetist.getId(),
                                workstationPair.getWorkstation1(),
                                weekendPair.getDay2(), week);
                        boolean weeklyWs2Day1 = solution.isAssignedWeekly(anaesthetist.getId(),
                                workstationPair.getWorkstation2(),
                                weekendPair.getDay1(), week);
                        boolean weeklyWs2Day2 = solution.isAssignedWeekly(anaesthetist.getId(),
                                workstationPair.getWorkstation2(),
                                weekendPair.getDay2(), week);

                        if ((weeklyWs1Day1 || weeklyWs1Day2) && !(weeklyWs2Day1 && weeklyWs2Day2)) {
                            violations.add(new ConstraintViolation(
                                    ConstraintType.HC11,
                                    String.format(
                                            "Weekly mandatory pairing violation: %s assigned but %s not paired on weekend days %d-%d",
                                            workstationPair.getWorkstation1(), workstationPair.getWorkstation2(),
                                            weekendPair.getDay1(), weekendPair.getDay2()),
                                    anaesthetist.getId(), workstationPair.getWorkstation1(), weekendPair.getDay1(), 1));
                        }
                    }
                }
            }
        }

        return violations;
    }

    public boolean hasAnyHardConstraintViolations(Solution solution, ProblemInstance instance) {
        return !checkAllHardConstraints(solution, instance).isEmpty();
    }

    public void printHardConstraintViolations(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = checkAllHardConstraints(solution, instance);

        if (violations.isEmpty()) {
            System.out.println("✓ All hard constraints satisfied");
            return;
        }

        System.out.println("✗ Hard constraint violations found:");
        System.out.println("=====================================");

        // Group violations by constraint type
        Map<ConstraintType, List<ConstraintViolation>> groupedViolations = new HashMap<>();
        for (ConstraintViolation violation : violations) {
            groupedViolations.computeIfAbsent(violation.getConstraintType(), k -> new ArrayList<>())
                    .add(violation);
        }

        // Print violations by constraint type
        for (ConstraintType type : ConstraintType.values()) {
            if (type.isHardConstraint() && groupedViolations.containsKey(type)) {
                List<ConstraintViolation> typeViolations = groupedViolations.get(type);
                System.out.printf("%s (%s): %d violations\n",
                        type.name(), type.getDescription(), typeViolations.size());

                for (ConstraintViolation violation : typeViolations) {
                    System.out.println("  " + violation.getDescription());
                    if (violation.getAnaesthetistId() != null) {
                        System.out.printf("    Anaesthetist: %s", violation.getAnaesthetistId());
                        if (violation.getWorkstationId() != null) {
                            System.out.printf(", Workstation: %s", violation.getWorkstationId());
                        }
                        if (violation.getDayNumber() > 0) {
                            System.out.printf(", Day: %d", violation.getDayNumber());
                        }
                        System.out.println();
                    }
                }
                System.out.println();
            }
        }

        System.out.printf("Total hard constraint violations: %d\n", violations.size());
        System.out.println("=====================================");
    }

    public Map<ConstraintType, Integer> getHardConstraintViolationCounts(Solution solution, ProblemInstance instance) {
        List<ConstraintViolation> violations = checkAllHardConstraints(solution, instance);
        Map<ConstraintType, Integer> counts = new HashMap<>();

        // Initialize all hard constraint counts to 0
        for (ConstraintType type : ConstraintType.values()) {
            if (type.isHardConstraint()) {
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

    public boolean isSolutionFeasible(Solution solution, ProblemInstance instance) {
        return !hasAnyHardConstraintViolations(solution, instance);
    }
}