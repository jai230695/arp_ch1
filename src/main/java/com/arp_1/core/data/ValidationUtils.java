// File: src/main/java/com/arp/core/data/ValidationUtils.java
package com.arp_1.core.data;

import com.arp_1.core.models.*;
import java.util.*;

public class ValidationUtils {

    public static void validateProblemInstance(ProblemInstance instance) {
        validateAnaesthetists(instance);
        validateWorkstations(instance);
        validatePlanningDays(instance);
        validateWorkstationDemands(instance);
        validateConsistency(instance);

        System.out.println("Problem instance validation completed successfully.");
    }

    private static void validateAnaesthetists(ProblemInstance instance) {
        List<Anaesthetist> anaesthetists = instance.getAnaesthetists();

        if (anaesthetists.isEmpty()) {
            throw new IllegalStateException("No anaesthetists found in problem instance");
        }

        // Check for duplicate IDs
        Set<String> ids = new HashSet<>();
        for (Anaesthetist a : anaesthetists) {
            if (!ids.add(a.getId())) {
                throw new IllegalStateException("Duplicate anaesthetist ID: " + a.getId());
            }
        }

        // Validate types
        long juniorCount = anaesthetists.stream().filter(Anaesthetist::isJunior).count();
        long seniorCount = anaesthetists.stream().filter(Anaesthetist::isSenior).count();

        System.out.println("Anaesthetists validation: " + anaesthetists.size() + " total (" +
                juniorCount + " Junior, " + seniorCount + " Senior)");
    }

    private static void validateWorkstations(ProblemInstance instance) {
        List<Workstation> workstations = instance.getWorkstations();

        if (workstations.isEmpty()) {
            throw new IllegalStateException("No workstations found in problem instance");
        }

        // Check for duplicate IDs
        Set<String> ids = new HashSet<>();
        for (Workstation w : workstations) {
            if (!ids.add(w.getId())) {
                throw new IllegalStateException("Duplicate workstation ID: " + w.getId());
            }
        }

        long monthlyCount = workstations.stream().filter(Workstation::isMonthlyRoster).count();
        long weeklyCount = workstations.stream().filter(Workstation::isWeeklyRoster).count();

        System.out.println("Workstations validation: " + workstations.size() + " total (" +
                monthlyCount + " Monthly, " + weeklyCount + " Weekly)");
    }

    private static void validatePlanningDays(ProblemInstance instance) {
        List<PlanningDay> days = instance.getPlanningDays();

        if (days.isEmpty()) {
            throw new IllegalStateException("No planning days found in problem instance");
        }

        // Check day sequence
        days.sort((d1, d2) -> Integer.compare(d1.getDayNumber(), d2.getDayNumber()));

        for (int i = 0; i < days.size(); i++) {
            PlanningDay day = days.get(i);
            int expectedDay = i + 1;

            if (day.getDayNumber() != expectedDay) {
                throw new IllegalStateException("Missing or incorrect day number. Expected: " +
                        expectedDay + ", Found: " + day.getDayNumber());
            }
        }

        System.out.println("Planning days validation: " + days.size() + " days (1-" + days.size() + ")");
    }

    private static void validateWorkstationDemands(ProblemInstance instance) {
        Map<String, Map<Integer, Integer>> demands = instance.getWorkstationDemands();

        for (Workstation workstation : instance.getWorkstations()) {
            String workstationId = workstation.getId();

            if (!demands.containsKey(workstationId)) {
                throw new IllegalStateException("No demand data found for workstation: " + workstationId);
            }

            Map<Integer, Integer> workstationDemands = demands.get(workstationId);

            for (PlanningDay day : instance.getPlanningDays()) {
                int dayNumber = day.getDayNumber();

                if (!workstationDemands.containsKey(dayNumber)) {
                    throw new IllegalStateException("No demand data for workstation " + workstationId +
                            " on day " + dayNumber);
                }

                int demand = workstationDemands.get(dayNumber);
                if (demand < 0) {
                    throw new IllegalStateException("Negative demand for workstation " + workstationId +
                            " on day " + dayNumber + ": " + demand);
                }
            }
        }

        System.out.println("Workstation demands validation: All workstations have complete demand data");
    }

    private static void validateConsistency(ProblemInstance instance) {
        // Check that all anaesthetists have preference data for all workstations
        for (Anaesthetist anaesthetist : instance.getAnaesthetists()) {
            for (Workstation workstation : instance.getWorkstations()) {
                // Check if preference exists (including 0 for not qualified)
                Map<String, Integer> preferences = anaesthetist.getLocationPreferences();
                if (!preferences.containsKey(workstation.getId())) {
                    throw new IllegalStateException("Missing preference data for anaesthetist " +
                            anaesthetist.getId() + " and workstation " +
                            workstation.getId());
                }
            }
        }

        // Validate that weekend holidays are within planning period
        int maxDay = instance.getPlanningDays().stream()
                .mapToInt(PlanningDay::getDayNumber)
                .max()
                .orElse(0);

        for (Integer holiday : instance.getWeekendHolidays()) {
            if (holiday < 1 || holiday > maxDay) {
                throw new IllegalStateException("Weekend holiday day " + holiday +
                        " is outside planning period (1-" + maxDay + ")");
            }
        }

        System.out.println("Consistency validation: All cross-references are valid");
    }

    public static void printProblemInstanceSummary(ProblemInstance instance) {
        System.out.println("\n============================================================");
        System.out.println("PROBLEM INSTANCE SUMMARY");
        System.out.println("============================================================");

        // Anaesthetists summary
        List<Anaesthetist> anaesthetists = instance.getAnaesthetists();
        long activeCount = anaesthetists.stream().filter(Anaesthetist::isActive).count();
        long juniorCount = anaesthetists.stream().filter(Anaesthetist::isJunior).count();
        long seniorCount = anaesthetists.stream().filter(Anaesthetist::isSenior).count();

        System.out.println("Anaesthetists:");
        System.out.println("  Total: " + anaesthetists.size() + " (" + activeCount + " active)");
        System.out.println("  Junior: " + juniorCount + ", Senior: " + seniorCount);

        // Workstations summary
        List<Workstation> workstations = instance.getWorkstations();
        long monthlyCount = workstations.stream().filter(Workstation::isMonthlyRoster).count();
        long weeklyCount = workstations.stream().filter(Workstation::isWeeklyRoster).count();

        System.out.println("\nWorkstations:");
        System.out.println("  Total: " + workstations.size());
        System.out.println("  Monthly: " + monthlyCount + ", Weekly: " + weeklyCount);

        // Planning period summary
        System.out.println("\nPlanning Period:");
        System.out.println("  Days: " + instance.getPlanningDays().size());
        System.out.println("  Weekend/Holidays: " + instance.getWeekendHolidays().size());
        System.out.println("  Pre-Holidays: " + instance.getPreHolidays().size());

        // Requests summary
        List<Request> requests = instance.getRequests();
        Map<String, Long> requestTypes = requests.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Request::getRequestType,
                        java.util.stream.Collectors.counting()));

        System.out.println("\nRequests:");
        System.out.println("  Total: " + requests.size());
        for (Map.Entry<String, Long> entry : requestTypes.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }

        // Demand summary
        int totalDemand = 0;
        for (Workstation workstation : workstations) {
            for (PlanningDay day : instance.getPlanningDays()) {
                totalDemand += instance.getWorkstationDemand(workstation.getId(), day.getDayNumber());
            }
        }

        System.out.println("\nDemand:");
        System.out.println("  Total assignments needed: " + totalDemand);

        System.out.println("============================================================");
    }
}