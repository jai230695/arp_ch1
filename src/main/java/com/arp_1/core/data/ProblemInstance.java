// File: src/main/java/com/arp/core/data/ProblemInstance.java
package com.arp_1.core.data;

import com.arp_1.core.models.*;
import java.util.*;
import java.util.stream.Collectors;

public class ProblemInstance {
    private List<Anaesthetist> anaesthetists;
    private List<Workstation> workstations;
    private List<PlanningDay> planningDays;
    private List<Request> requests;
    private Map<String, Map<Integer, Integer>> workstationDemands;
    private List<WorkstationPair> workstationPairs;
    private Set<Integer> weekendHolidays;
    private List<WeekendPair> weekendPairs;
    private Set<Integer> preHolidays;
    private Map<String, Map<String, Integer>> historicalAllDays;
    private Map<String, Map<String, Integer>> historicalWeekend;
    private Map<String, Map<String, Integer>> historicalPreHoliday;
    private List<Assignment> previousMonthAssignments;

    // Configuration constants
    private static final double MAX_DAILY_WORKLOAD = 2.0; // Based on your MILP model
    private static final int MAX_WEEKLY_WORKING_DAYS = 7;

    public ProblemInstance(List<Anaesthetist> anaesthetists,
            List<Workstation> workstations,
            List<PlanningDay> planningDays,
            List<Request> requests,
            Map<String, Map<Integer, Integer>> workstationDemands,
            List<WorkstationPair> workstationPairs,
            Set<Integer> weekendHolidays,
            List<WeekendPair> weekendPairs,
            Set<Integer> preHolidays,
            Map<String, Map<String, Integer>> historicalAllDays,
            Map<String, Map<String, Integer>> historicalWeekend,
            Map<String, Map<String, Integer>> historicalPreHoliday,
            List<Assignment> previousMonthAssignments) {

        this.anaesthetists = anaesthetists;
        this.workstations = workstations;
        this.planningDays = planningDays;
        this.requests = requests;
        this.workstationDemands = workstationDemands;
        this.workstationPairs = workstationPairs;
        this.weekendHolidays = weekendHolidays;
        this.weekendPairs = weekendPairs;
        this.preHolidays = preHolidays;
        this.historicalAllDays = historicalAllDays;
        this.historicalWeekend = historicalWeekend;
        this.historicalPreHoliday = historicalPreHoliday;
        this.previousMonthAssignments = previousMonthAssignments;
    }

    // Helper methods for accessing filtered data
    public List<Workstation> getMonthlyWorkstations() {
        return workstations.stream()
                .filter(Workstation::isMonthlyRoster)
                .collect(Collectors.toList());
    }

    public List<Workstation> getWeeklyWorkstations() {
        return workstations.stream()
                .filter(Workstation::isWeeklyRoster)
                .collect(Collectors.toList());
    }

    public List<Anaesthetist> getJuniorAnaesthetists() {
        return anaesthetists.stream()
                .filter(Anaesthetist::isJunior)
                .collect(Collectors.toList());
    }

    public List<Anaesthetist> getSeniorAnaesthetists() {
        return anaesthetists.stream()
                .filter(Anaesthetist::isSenior)
                .collect(Collectors.toList());
    }

    public List<Anaesthetist> getActiveAnaesthetists() {
        return anaesthetists.stream()
                .filter(Anaesthetist::isActive)
                .collect(Collectors.toList());
    }

    public Workstation getWorkstationById(String id) {
        return workstations.stream()
                .filter(w -> w.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public Anaesthetist getAnaesthetistById(String id) {
        return anaesthetists.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public PlanningDay getPlanningDay(int dayNumber) {
        return planningDays.stream()
                .filter(d -> d.getDayNumber() == dayNumber)
                .findFirst()
                .orElse(null);
    }

    public List<PlanningDay> getWeekDays(int week) {
        return planningDays.stream()
                .filter(d -> d.getWeek() == week)
                .collect(Collectors.toList());
    }

    public int getWorkstationDemand(String workstationId, int dayNumber) {
        return workstationDemands
                .getOrDefault(workstationId, new HashMap<>())
                .getOrDefault(dayNumber, 0);
    }

    public List<Request> getRequestsForAnaesthetist(String anaesthetistId) {
        return requests.stream()
                .filter(r -> r.getAnaesthetistId().equals(anaesthetistId))
                .collect(Collectors.toList());
    }

    public List<Request> getRequestsForDay(int dayNumber) {
        return requests.stream()
                .filter(r -> r.getDayNumber() == dayNumber)
                .collect(Collectors.toList());
    }

    public Optional<Request> getRequest(String anaesthetistId, int dayNumber) {
        return requests.stream()
                .filter(r -> r.getAnaesthetistId().equals(anaesthetistId) && r.getDayNumber() == dayNumber)
                .findFirst();
    }

    public boolean isAnaesthetistUnavailable(String anaesthetistId, int dayNumber) {
        return getRequest(anaesthetistId, dayNumber)
                .map(Request::isAbsenceRequest)
                .orElse(false);
    }

    public boolean hasNoCallRequest(String anaesthetistId, int dayNumber) {
        return getRequest(anaesthetistId, dayNumber)
                .map(Request::isNoCallRequest)
                .orElse(false);
    }

    public boolean hasMorningShiftRequest(String anaesthetistId, int dayNumber) {
        return getRequest(anaesthetistId, dayNumber)
                .map(Request::isMorningShiftRequest)
                .orElse(false);
    }

    public boolean hasEveningShiftRequest(String anaesthetistId, int dayNumber) {
        return getRequest(anaesthetistId, dayNumber)
                .map(Request::isEveningShiftRequest)
                .orElse(false);
    }

    public int getHistoricalAssignments(String anaesthetistId, String workstationId) {
        return historicalAllDays
                .getOrDefault(anaesthetistId, new HashMap<>())
                .getOrDefault(workstationId, 0);
    }

    public int getHistoricalWeekendAssignments(String anaesthetistId, String workstationId) {
        return historicalWeekend
                .getOrDefault(anaesthetistId, new HashMap<>())
                .getOrDefault(workstationId, 0);
    }

    public int getHistoricalPreHolidayAssignments(String anaesthetistId, String workstationId) {
        return historicalPreHoliday
                .getOrDefault(anaesthetistId, new HashMap<>())
                .getOrDefault(workstationId, 0);
    }

    public boolean isWeekendOrHoliday(int dayNumber) {
        return weekendHolidays.contains(dayNumber);
    }

    public boolean isPreHoliday(int dayNumber) {
        return preHolidays.contains(dayNumber);
    }

    public Optional<WeekendPair> getWeekendPairContaining(int dayNumber) {
        return weekendPairs.stream()
                .filter(pair -> pair.containsDay(dayNumber))
                .findFirst();
    }

    public List<Anaesthetist> getQualifiedAnaesthetists(String workstationId) {
        return anaesthetists.stream()
                .filter(Anaesthetist::isActive)
                .filter(a -> a.isQualifiedFor(workstationId))
                .collect(Collectors.toList());
    }

    public List<Anaesthetist> getPreferredAnaesthetists(String workstationId) {
        return anaesthetists.stream()
                .filter(Anaesthetist::isActive)
                .filter(a -> a.hasPreferenceFor(workstationId))
                .collect(Collectors.toList());
    }

    // Configuration getters
    public double getMaxDailyWorkload() {
        return MAX_DAILY_WORKLOAD;
    }

    public int getMaxWeeklyWorkingDays() {
        return MAX_WEEKLY_WORKING_DAYS;
    }

    // Basic getters
    public List<Anaesthetist> getAnaesthetists() {
        return anaesthetists;
    }

    public List<Workstation> getWorkstations() {
        return workstations;
    }

    public List<Workstation> getAllWorkstations() {
        return workstations;
    }

    public List<PlanningDay> getPlanningDays() {
        return planningDays;
    }

    public List<Request> getRequests() {
        return requests;
    }

    public Map<String, Map<Integer, Integer>> getWorkstationDemands() {
        return workstationDemands;
    }

    public List<WorkstationPair> getWorkstationPairs() {
        return workstationPairs;
    }

    public Set<Integer> getWeekendHolidays() {
        return weekendHolidays;
    }

    public List<WeekendPair> getWeekendPairs() {
        return weekendPairs;
    }

    public Set<Integer> getPreHolidays() {
        return preHolidays;
    }

    public List<Assignment> getPreviousMonthAssignments() {
        return previousMonthAssignments;
    }

    @Override
    public String toString() {
        return String.format(
                "ProblemInstance{anaesthetists=%d, workstations=%d, days=%d, requests=%d, weekendHolidays=%d}",
                anaesthetists.size(), workstations.size(), planningDays.size(),
                requests.size(), weekendHolidays.size());
    }
}
