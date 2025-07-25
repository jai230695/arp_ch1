// File: src/main/java/com/arp/core/data/DataLoader.java
package com.arp_1.core.data;

import com.arp_1.core.models.*;
import java.io.IOException;
import java.util.*;

public class DataLoader {

    public static ProblemInstance loadProblemInstance(String dataPath) {
        try {
            System.out.println("Loading problem instance from: " + dataPath);

            // Ensure data path ends with separator
            if (!dataPath.endsWith("/") && !dataPath.endsWith("\\")) {
                dataPath += "/";
            }

            // Load all data components
            List<Anaesthetist> anaesthetists = loadAnaesthetists(dataPath);
            List<Workstation> workstations = loadWorkstations(dataPath);
            List<PlanningDay> planningDays = loadPlanningDays(dataPath);

            // Load preferences
            loadAnaesthetistPreferences(dataPath, anaesthetists);

            // Load requests
            List<Request> requests = loadRequests(dataPath);

            // Load workstation demands
            Map<String, Map<Integer, Integer>> workstationDemands = loadWorkstationDemands(dataPath);

            // Load workstation pairs
            List<WorkstationPair> workstationPairs = loadWorkstationPairs(dataPath);

            // Load weekend holidays and pairs
            Set<Integer> weekendHolidays = loadWeekendHolidays(dataPath);
            List<WeekendPair> weekendPairs = loadWeekendPairs(dataPath);
            Set<Integer> preHolidays = loadPreHolidays(dataPath);

            // Update planning days with weekend/holiday information
            updatePlanningDaysWithHolidays(planningDays, weekendHolidays, preHolidays);

            // Load historical data
            Map<String, Map<String, Integer>> historicalAllDays = loadHistoricalData(
                    dataPath + "initial_all_days_distribution.csv");
            Map<String, Map<String, Integer>> historicalWeekend = loadHistoricalData(
                    dataPath + "initial_weekend_holiday_distribution.csv");
            Map<String, Map<String, Integer>> historicalPreHoliday = loadHistoricalData(
                    dataPath + "initial_preholiday_distribution.csv");

            // Load previous month data
            List<Assignment> previousMonthAssignments = loadPreviousMonth(dataPath);

            // Create and return problem instance
            ProblemInstance instance = new ProblemInstance(
                    anaesthetists, workstations, planningDays, requests,
                    workstationDemands, workstationPairs, weekendHolidays,
                    weekendPairs, preHolidays, historicalAllDays,
                    historicalWeekend, historicalPreHoliday, previousMonthAssignments);

            System.out.println("Successfully loaded problem instance:");
            System.out.println("  Anaesthetists: " + anaesthetists.size());
            System.out.println("  Workstations: " + workstations.size());
            System.out.println("  Planning Days: " + planningDays.size());
            System.out.println("  Requests: " + requests.size());
            System.out.println("  Weekend/Holidays: " + weekendHolidays.size());

            return instance;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load problem instance: " + e.getMessage(), e);
        }
    }

    private static List<Anaesthetist> loadAnaesthetists(String dataPath) throws IOException {
        List<Anaesthetist> anaesthetists = new ArrayList<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "anaesthetists.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "ID", "Type", "Active");

            String id = record.get("ID");
            String type = record.get("Type");
            boolean isActive = CSVParser.parseBoolean(record.get("Active"));

            Anaesthetist anaesthetist = new Anaesthetist(id, type, isActive);
            anaesthetists.add(anaesthetist);
        }

        return anaesthetists;
    }

    private static List<Workstation> loadWorkstations(String dataPath) throws IOException {
        List<Workstation> workstations = new ArrayList<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "workstations.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "ID", "Name", "Type", "Shift",
                    "MaxWorkingHour", "RestDay", "StartTime",
                    "EndTime", "Weight", "RosterType");

            String id = record.get("ID");
            String name = record.get("Name");
            String type = record.get("Type");
            String shift = record.get("Shift");
            int maxWorkingHour = CSVParser.parseInt(record.get("MaxWorkingHour"), 0);
            int restDay = CSVParser.parseInt(record.get("RestDay"), 0);
            int startTime = CSVParser.parseInt(record.get("StartTime"), 0);
            int endTime = CSVParser.parseInt(record.get("EndTime"), 0);
            double weight = CSVParser.parseDouble(record.get("Weight"), 1.0);
            String rosterType = record.get("RosterType");

            Workstation workstation = new Workstation(id, name, type, shift,
                    maxWorkingHour, restDay,
                    startTime, endTime,
                    weight, rosterType);
            workstations.add(workstation);
        }

        return workstations;
    }

    private static List<PlanningDay> loadPlanningDays(String dataPath) throws IOException {
        List<PlanningDay> planningDays = new ArrayList<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "planning_days.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "DayNumber", "Day", "Week", "ActualWeek", "Date");

            int dayNumber = CSVParser.parseInt(record.get("DayNumber"), 0);
            String dayOfWeek = record.get("Day");
            int week = CSVParser.parseInt(record.get("Week"), 0);
            int actualWeek = CSVParser.parseInt(record.get("ActualWeek"), 0);
            String date = record.get("Date");

            PlanningDay planningDay = new PlanningDay(dayNumber, dayOfWeek, week, actualWeek, date);
            planningDays.add(planningDay);
        }

        return planningDays;
    }

    private static void loadAnaesthetistPreferences(String dataPath, List<Anaesthetist> anaesthetists)
            throws IOException {
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "anaesthetist_preferences.csv");

        // Create a map for quick lookup
        Map<String, Anaesthetist> anaesthetistMap = new HashMap<>();
        for (Anaesthetist a : anaesthetists) {
            anaesthetistMap.put(a.getId(), a);
        }

        for (Map<String, String> record : records) {
            String anaesthetistId = record.get("Anaesthetist");
            Anaesthetist anaesthetist = anaesthetistMap.get(anaesthetistId);

            if (anaesthetist != null) {
                // Load preferences for all workstation columns
                String[] workstations = { "CGOT", "CICU", "SICU", "CCT", "SCT", "SGOT", "PWOT",
                        "OHMAU", "OHMIU", "MMAU", "MMIU", "MCT", "MWK",
                        "MPMIS", "EU1", "EWK", "EU2" };

                for (String workstation : workstations) {
                    if (record.containsKey(workstation)) {
                        int preference = CSVParser.parseInt(record.get(workstation), 0);
                        anaesthetist.setLocationPreference(workstation, preference);
                    }
                }
            }
        }
    }

    private static List<Request> loadRequests(String dataPath) throws IOException {
        List<Request> requests = new ArrayList<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "anaesthetist_requests.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "Anaesthetist", "DayNumber", "RequestType");

            String anaesthetistId = record.get("Anaesthetist");
            int dayNumber = CSVParser.parseInt(record.get("DayNumber"), 0);
            String requestType = record.get("RequestType");

            Request request = new Request(anaesthetistId, dayNumber, requestType);
            requests.add(request);
        }

        return requests;
    }

    private static Map<String, Map<Integer, Integer>> loadWorkstationDemands(String dataPath) throws IOException {
        Map<String, Map<Integer, Integer>> demands = new HashMap<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "workstation_demands.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "Workstation", "DayNumber", "RequiredCount");

            String workstation = record.get("Workstation");
            int dayNumber = CSVParser.parseInt(record.get("DayNumber"), 0);
            int requiredCount = CSVParser.parseInt(record.get("RequiredCount"), 0);

            demands.computeIfAbsent(workstation, k -> new HashMap<>())
                    .put(dayNumber, requiredCount);
        }

        return demands;
    }

    private static List<WorkstationPair> loadWorkstationPairs(String dataPath) throws IOException {
        List<WorkstationPair> pairs = new ArrayList<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "workstation_pairs.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "Workstation1", "Workstation2");

            String workstation1 = record.get("Workstation1");
            String workstation2 = record.get("Workstation2");

            WorkstationPair pair = new WorkstationPair(workstation1, workstation2);
            pairs.add(pair);
        }

        return pairs;
    }

    private static Set<Integer> loadWeekendHolidays(String dataPath) throws IOException {
        Set<Integer> weekendHolidays = new HashSet<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "weekend_holidays.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "DayNumber");
            int dayNumber = CSVParser.parseInt(record.get("DayNumber"), 0);
            weekendHolidays.add(dayNumber);
        }

        return weekendHolidays;
    }

    private static List<WeekendPair> loadWeekendPairs(String dataPath) throws IOException {
        List<WeekendPair> pairs = new ArrayList<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "weekend_pairs.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "DayNumber1", "DayNumber2");

            int day1 = CSVParser.parseInt(record.get("DayNumber1"), 0);
            int day2 = CSVParser.parseInt(record.get("DayNumber2"), 0);

            WeekendPair pair = new WeekendPair(day1, day2);
            pairs.add(pair);
        }

        return pairs;
    }

    private static Set<Integer> loadPreHolidays(String dataPath) throws IOException {
        Set<Integer> preHolidays = new HashSet<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "pre_holidays.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "DayNumber");
            int dayNumber = CSVParser.parseInt(record.get("DayNumber"), 0);
            preHolidays.add(dayNumber);
        }

        return preHolidays;
    }

    private static void updatePlanningDaysWithHolidays(List<PlanningDay> planningDays,
            Set<Integer> weekendHolidays,
            Set<Integer> preHolidays) {
        for (PlanningDay day : planningDays) {
            if (weekendHolidays.contains(day.getDayNumber())) {
                day.setWeekendOrHoliday(true);
            }
            if (preHolidays.contains(day.getDayNumber())) {
                day.setPreHoliday(true);
            }
        }
    }

    private static Map<String, Map<String, Integer>> loadHistoricalData(String filePath) throws IOException {
        Map<String, Map<String, Integer>> historicalData = new HashMap<>();
        List<Map<String, String>> records = CSVParser.parseCSV(filePath);

        for (Map<String, String> record : records) {
            String anaesthetistId = record.get("Anaesthetist");
            if (anaesthetistId != null) {
                Map<String, Integer> assignments = new HashMap<>();

                // Load all workstation assignments
                String[] workstations = { "CGOT", "CICU", "SICU", "CCT", "SCT", "SGOT", "PWOT",
                        "OHMAU", "OHMIU", "MMAU", "MMIU", "MCT", "MWK",
                        "MPMIS", "EU1", "EWK", "EU2" };

                for (String workstation : workstations) {
                    if (record.containsKey(workstation)) {
                        int count = CSVParser.parseInt(record.get(workstation), 0);
                        assignments.put(workstation, count);
                    }
                }

                historicalData.put(anaesthetistId, assignments);
            }
        }

        return historicalData;
    }

    private static List<Assignment> loadPreviousMonth(String dataPath) throws IOException {
        List<Assignment> assignments = new ArrayList<>();
        List<Map<String, String>> records = CSVParser.parseCSV(dataPath + "previous_month.csv");

        for (Map<String, String> record : records) {
            CSVParser.validateRequiredFields(record, "Anaesthetist", "Workstation", "DayNumber");

            String anaesthetistId = record.get("Anaesthetist");
            String workstationId = record.get("Workstation");
            int dayNumber = CSVParser.parseInt(record.get("DayNumber"), 0);

            // Determine if it's monthly or weekly based on workstation
            // This is a simplified determination - you might need to cross-reference with
            // workstation data
            boolean isMonthly = isMonthlyWorkstation(workstationId);
            int week = ((dayNumber - 1) / 7) + 1; // Simple week calculation

            Assignment assignment = new Assignment(anaesthetistId, workstationId, dayNumber, week, isMonthly);
            assignments.add(assignment);
        }

        return assignments;
    }

    private static boolean isMonthlyWorkstation(String workstationId) {
        // Define which workstations are monthly (on-call)
        Set<String> monthlyWorkstations = Set.of("CGOT", "CICU", "SICU", "CCT", "SCT", "SGOT", "PWOT");
        return monthlyWorkstations.contains(workstationId);
    }
}