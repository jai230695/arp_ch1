// File: src/main/java/com/arp/core/models/Solution.java
package com.arp_1.core.models;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete implementation of Solution class for Anaesthetist Rostering Problem
 * Stores all assignments, constraints, and metadata for both monthly and weekly
 * rosters
 */
public class Solution {

    // Core assignment storage
    private Map<String, Map<String, Set<Integer>>> monthlyAssignments;
    private Map<Integer, Map<String, Map<String, Map<Integer, Boolean>>>> weeklyAssignments;

    // Constraint and metadata storage
    private Map<String, Set<Integer>> restDayRestrictions;
    private Map<String, Integer> constraintViolations;
    private Map<String, Object> statistics;
    private Map<String, Object> metadata;

    // Solution quality metrics
    private double objectiveValue;
    private int hardConstraintViolations;
    private int softConstraintViolations;
    private boolean feasible;

    // Construction metadata
    private String constructionMethod;
    private long creationTime;
    private long computationTime;
    private String solverVersion;

    /**
     * Default constructor - initializes all data structures
     */
    public Solution() {
        this.monthlyAssignments = new ConcurrentHashMap<>();
        this.weeklyAssignments = new ConcurrentHashMap<>();
        this.restDayRestrictions = new ConcurrentHashMap<>();
        this.constraintViolations = new ConcurrentHashMap<>();
        this.statistics = new ConcurrentHashMap<>();
        this.metadata = new ConcurrentHashMap<>();

        this.objectiveValue = 0.0;
        this.hardConstraintViolations = 0;
        this.softConstraintViolations = 0;
        this.feasible = true;

        this.constructionMethod = "UNKNOWN";
        this.creationTime = System.currentTimeMillis();
        this.computationTime = 0L;
        this.solverVersion = "1.0";
    }

    /**
     * Copy constructor
     */
    public Solution(Solution other) {
        this();
        if (other != null) {
            copyFrom(other);
        }
    }

    // ============================================================================
    // MONTHLY ASSIGNMENT METHODS
    // ============================================================================

    /**
     * Assign anaesthetist to monthly workstation on specific day
     */
    public void assignMonthly(String anaesthetistId, String workstationId, int dayNumber) {
        monthlyAssignments
                .computeIfAbsent(anaesthetistId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(workstationId, k -> ConcurrentHashMap.newKeySet())
                .add(dayNumber);
    }

    /**
     * Remove monthly assignment
     */
    public void removeMonthlyAssignment(String anaesthetistId, String workstationId, int dayNumber) {
        Map<String, Set<Integer>> anaesthetistAssignments = monthlyAssignments.get(anaesthetistId);
        if (anaesthetistAssignments != null) {
            Set<Integer> workstationDays = anaesthetistAssignments.get(workstationId);
            if (workstationDays != null) {
                workstationDays.remove(dayNumber);
                if (workstationDays.isEmpty()) {
                    anaesthetistAssignments.remove(workstationId);
                }
            }
            if (anaesthetistAssignments.isEmpty()) {
                monthlyAssignments.remove(anaesthetistId);
            }
        }
    }

    /**
     * Check if anaesthetist is assigned to specific monthly workstation on specific
     * day
     */
    public boolean isAssignedMonthly(String anaesthetistId, String workstationId, int dayNumber) {
        Map<String, Set<Integer>> anaesthetistAssignments = monthlyAssignments.get(anaesthetistId);
        if (anaesthetistAssignments != null) {
            Set<Integer> workstationDays = anaesthetistAssignments.get(workstationId);
            return workstationDays != null && workstationDays.contains(dayNumber);
        }
        return false;
    }

    /**
     * Check if anaesthetist is assigned to ANY monthly workstation on specific day
     */
    public boolean isAssignedMonthlyAnyLocation(String anaesthetistId, int dayNumber) {
        Map<String, Set<Integer>> anaesthetistAssignments = monthlyAssignments.get(anaesthetistId);
        if (anaesthetistAssignments != null) {
            return anaesthetistAssignments.values().stream()
                    .anyMatch(days -> days.contains(dayNumber));
        }
        return false;
    }

    /**
     * Get all monthly workstations assigned to anaesthetist on specific day
     */
    public Set<String> getMonthlyAssignments(String anaesthetistId, int dayNumber) {
        Set<String> assignments = new HashSet<>();
        Map<String, Set<Integer>> anaesthetistAssignments = monthlyAssignments.get(anaesthetistId);
        if (anaesthetistAssignments != null) {
            for (Map.Entry<String, Set<Integer>> entry : anaesthetistAssignments.entrySet()) {
                if (entry.getValue().contains(dayNumber)) {
                    assignments.add(entry.getKey());
                }
            }
        }
        return assignments;
    }

    /**
     * Count total monthly assignments for anaesthetist at specific workstation
     */
    public int countMonthlyAssignments(String anaesthetistId, String workstationId) {
        Map<String, Set<Integer>> anaesthetistAssignments = monthlyAssignments.get(anaesthetistId);
        if (anaesthetistAssignments != null) {
            Set<Integer> workstationDays = anaesthetistAssignments.get(workstationId);
            return workstationDays != null ? workstationDays.size() : 0;
        }
        return 0;
    }

    /**
     * Count total monthly assignments for anaesthetist across all workstations
     */
    public int countTotalMonthlyAssignments(String anaesthetistId) {
        Map<String, Set<Integer>> anaesthetistAssignments = monthlyAssignments.get(anaesthetistId);
        if (anaesthetistAssignments != null) {
            return anaesthetistAssignments.values().stream()
                    .mapToInt(Set::size)
                    .sum();
        }
        return 0;
    }

    /**
     * Count weekend assignments for anaesthetist at specific workstation
     */
    public int countWeekendAssignments(String anaesthetistId, String workstationId, Set<Integer> weekendDays) {
        int count = 0;
        Map<String, Set<Integer>> anaesthetistAssignments = monthlyAssignments.get(anaesthetistId);
        if (anaesthetistAssignments != null) {
            Set<Integer> workstationDays = anaesthetistAssignments.get(workstationId);
            if (workstationDays != null) {
                for (Integer day : workstationDays) {
                    if (weekendDays.contains(day)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Count pre-holiday assignments for anaesthetist at specific workstation
     */
    public int countPreHolidayAssignments(String anaesthetistId, String workstationId, Set<Integer> preHolidayDays) {
        int count = 0;
        Map<String, Set<Integer>> anaesthetistAssignments = monthlyAssignments.get(anaesthetistId);
        if (anaesthetistAssignments != null) {
            Set<Integer> workstationDays = anaesthetistAssignments.get(workstationId);
            if (workstationDays != null) {
                for (Integer day : workstationDays) {
                    if (preHolidayDays.contains(day)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ============================================================================
    // WEEKLY ASSIGNMENT METHODS
    // ============================================================================

    /**
     * Assign anaesthetist to weekly workstation on specific day and week
     */
    public void assignWeekly(String anaesthetistId, String workstationId, int dayNumber, int week) {
        weeklyAssignments
                .computeIfAbsent(week, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(anaesthetistId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(workstationId, k -> new ConcurrentHashMap<>())
                .put(dayNumber, true);
    }

    /**
     * Remove weekly assignment
     */
    public void removeWeeklyAssignment(String anaesthetistId, String workstationId, int dayNumber, int week) {
        Map<String, Map<String, Map<Integer, Boolean>>> weekData = weeklyAssignments.get(week);
        if (weekData != null) {
            Map<String, Map<Integer, Boolean>> anaesthetistData = weekData.get(anaesthetistId);
            if (anaesthetistData != null) {
                Map<Integer, Boolean> workstationData = anaesthetistData.get(workstationId);
                if (workstationData != null) {
                    workstationData.remove(dayNumber);
                    if (workstationData.isEmpty()) {
                        anaesthetistData.remove(workstationId);
                    }
                }
                if (anaesthetistData.isEmpty()) {
                    weekData.remove(anaesthetistId);
                }
            }
            if (weekData.isEmpty()) {
                weeklyAssignments.remove(week);
            }
        }
    }

    /**
     * Check if anaesthetist is assigned to specific weekly workstation on specific
     * day and week
     */
    public boolean isAssignedWeekly(String anaesthetistId, String workstationId, int dayNumber, int week) {
        Map<String, Map<String, Map<Integer, Boolean>>> weekData = weeklyAssignments.get(week);
        if (weekData != null) {
            Map<String, Map<Integer, Boolean>> anaesthetistData = weekData.get(anaesthetistId);
            if (anaesthetistData != null) {
                Map<Integer, Boolean> workstationData = anaesthetistData.get(workstationId);
                if (workstationData != null) {
                    return Boolean.TRUE.equals(workstationData.get(dayNumber));
                }
            }
        }
        return false;
    }

    /**
     * Check if anaesthetist is assigned to ANY weekly workstation on specific day
     * and week
     */
    public boolean isAssignedWeeklyAnyLocation(String anaesthetistId, int dayNumber, int week) {
        Map<String, Map<String, Map<Integer, Boolean>>> weekData = weeklyAssignments.get(week);
        if (weekData != null) {
            Map<String, Map<Integer, Boolean>> anaesthetistData = weekData.get(anaesthetistId);
            if (anaesthetistData != null) {
                return anaesthetistData.values().stream()
                        .anyMatch(workstationData -> Boolean.TRUE.equals(workstationData.get(dayNumber)));
            }
        }
        return false;
    }

    /**
     * Get all weekly workstations assigned to anaesthetist on specific day and week
     */
    public Set<String> getWeeklyAssignments(String anaesthetistId, int dayNumber, int week) {
        Set<String> assignments = new HashSet<>();
        Map<String, Map<String, Map<Integer, Boolean>>> weekData = weeklyAssignments.get(week);
        if (weekData != null) {
            Map<String, Map<Integer, Boolean>> anaesthetistData = weekData.get(anaesthetistId);
            if (anaesthetistData != null) {
                for (Map.Entry<String, Map<Integer, Boolean>> entry : anaesthetistData.entrySet()) {
                    if (Boolean.TRUE.equals(entry.getValue().get(dayNumber))) {
                        assignments.add(entry.getKey());
                    }
                }
            }
        }
        return assignments;
    }

    /**
     * Count weekly assignments for anaesthetist at specific workstation in specific
     * week
     */
    public int countWeeklyAssignments(String anaesthetistId, String workstationId, int week) {
        int count = 0;
        Map<String, Map<String, Map<Integer, Boolean>>> weekData = weeklyAssignments.get(week);
        if (weekData != null) {
            Map<String, Map<Integer, Boolean>> anaesthetistData = weekData.get(anaesthetistId);
            if (anaesthetistData != null) {
                Map<Integer, Boolean> workstationData = anaesthetistData.get(workstationId);
                if (workstationData != null) {
                    count = (int) workstationData.values().stream()
                            .mapToLong(assigned -> Boolean.TRUE.equals(assigned) ? 1 : 0)
                            .sum();
                }
            }
        }
        return count;
    }

    /**
     * Count total weekly assignments for anaesthetist in specific week
     */
    public int countTotalWeeklyAssignments(String anaesthetistId, int week) {
        int count = 0;
        Map<String, Map<String, Map<Integer, Boolean>>> weekData = weeklyAssignments.get(week);
        if (weekData != null) {
            Map<String, Map<Integer, Boolean>> anaesthetistData = weekData.get(anaesthetistId);
            if (anaesthetistData != null) {
                for (Map<Integer, Boolean> workstationData : anaesthetistData.values()) {
                    count += (int) workstationData.values().stream()
                            .mapToLong(assigned -> Boolean.TRUE.equals(assigned) ? 1 : 0)
                            .sum();
                }
            }
        }
        return count;
    }

    /**
     * Get weekly assignments for specific week
     */
    public Map<String, Map<String, Map<Integer, Boolean>>> getWeeklyAssignments(int week) {
        return weeklyAssignments.getOrDefault(week, new HashMap<>());
    }

    // ============================================================================
    // WORKLOAD CALCULATION METHODS
    // ============================================================================

    /**
     * Calculate total workload for anaesthetist on specific day
     */
    public double getDayWorkload(String anaesthetistId, int dayNumber) {
        // This would require workstation weights - simplified implementation
        int monthlyCount = getMonthlyAssignments(anaesthetistId, dayNumber).size();

        // For weekly, we need to determine which week this day belongs to
        // Simplified: assume week = (dayNumber - 1) / 7 + 1
        int week = ((dayNumber - 1) / 7) + 1;
        int weeklyCount = getWeeklyAssignments(anaesthetistId, dayNumber, week).size();

        // Simplified workload calculation (each assignment = 1.0 weight)
        return monthlyCount + weeklyCount;
    }

    /**
     * Calculate total workload for anaesthetist across all days
     */
    public double getTotalWorkload(String anaesthetistId) {
        double totalWorkload = 0.0;

        // Add monthly workload
        totalWorkload += countTotalMonthlyAssignments(anaesthetistId);

        // Add weekly workload for all weeks
        for (int week = 1; week <= 4; week++) {
            totalWorkload += countTotalWeeklyAssignments(anaesthetistId, week);
        }

        return totalWorkload;
    }

    // ============================================================================
    // REST DAY RESTRICTION METHODS
    // ============================================================================

    /**
     * Add rest day restriction for anaesthetist
     */
    public void addRestDayRestriction(String anaesthetistId, int dayNumber) {
        restDayRestrictions
                .computeIfAbsent(anaesthetistId, k -> ConcurrentHashMap.newKeySet())
                .add(dayNumber);
    }

    /**
     * Remove rest day restriction
     */
    public void removeRestDayRestriction(String anaesthetistId, int dayNumber) {
        Set<Integer> restrictions = restDayRestrictions.get(anaesthetistId);
        if (restrictions != null) {
            restrictions.remove(dayNumber);
            if (restrictions.isEmpty()) {
                restDayRestrictions.remove(anaesthetistId);
            }
        }
    }

    /**
     * Check if anaesthetist has rest day restriction on specific day
     */
    public boolean hasRestDayRestriction(String anaesthetistId, int dayNumber) {
        Set<Integer> restrictions = restDayRestrictions.get(anaesthetistId);
        return restrictions != null && restrictions.contains(dayNumber);
    }

    /**
     * Get all rest day restrictions for anaesthetist
     */
    public Set<Integer> getRestDayRestrictions(String anaesthetistId) {
        return new HashSet<>(restDayRestrictions.getOrDefault(anaesthetistId, new HashSet<>()));
    }

    /**
     * Get all rest day restrictions
     */
    public Map<String, Set<Integer>> getRestDayRestrictions() {
        Map<String, Set<Integer>> copy = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : restDayRestrictions.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    // ============================================================================
    // CONSTRAINT VIOLATION METHODS
    // ============================================================================

    /**
     * Add constraint violation count
     */
    public void addConstraintViolation(String constraintType, int violations) {
        constraintViolations.merge(constraintType, violations, Integer::sum);

        // Update total violation counts
        if (constraintType.startsWith("HC")) {
            hardConstraintViolations += violations;
        } else if (constraintType.startsWith("SC")) {
            softConstraintViolations += violations;
        }
    }

    /**
     * Set constraint violation count (overwrites existing)
     */
    public void setConstraintViolation(String constraintType, int violations) {
        Integer oldValue = constraintViolations.put(constraintType, violations);

        // Update total violation counts
        if (constraintType.startsWith("HC")) {
            if (oldValue != null) {
                hardConstraintViolations -= oldValue;
            }
            hardConstraintViolations += violations;
        } else if (constraintType.startsWith("SC")) {
            if (oldValue != null) {
                softConstraintViolations -= oldValue;
            }
            softConstraintViolations += violations;
        }
    }

    /**
     * Get constraint violation count for specific type
     */
    public int getConstraintViolation(String constraintType) {
        return constraintViolations.getOrDefault(constraintType, 0);
    }

    /**
     * Get all constraint violations
     */
    public Map<String, Integer> getConstraintViolations() {
        return new HashMap<>(constraintViolations);
    }

    /**
     * Get total hard constraint violations
     */
    public int getTotalHardConstraintViolations() {
        return constraintViolations.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("HC"))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    /**
     * Get total soft constraint violations
     */
    public int getTotalSoftConstraintViolations() {
        return constraintViolations.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("SC"))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    /**
     * Check if solution has hard constraint violations
     */
    public boolean hasHardConstraintViolations() {
        return getTotalHardConstraintViolations() > 0;
    }

    /**
     * Clear all constraint violations
     */
    public void clearConstraintViolations() {
        constraintViolations.clear();
        hardConstraintViolations = 0;
        softConstraintViolations = 0;
    }

    // ============================================================================
    // STATISTICS AND METADATA METHODS
    // ============================================================================

    /**
     * Add statistic
     */
    public void addStatistic(String key, Object value) {
        statistics.put(key, value);
    }

    /**
     * Get statistic
     */
    public Object getStatistic(String key) {
        return statistics.get(key);
    }

    /**
     * Get all statistics
     */
    public Map<String, Object> getStatistics() {
        return new HashMap<>(statistics);
    }

    /**
     * Add metadata
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Get metadata
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Get all metadata
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    // ============================================================================
    // SOLUTION QUALITY GETTERS AND SETTERS
    // ============================================================================

    public double getObjectiveValue() {
        return objectiveValue;
    }

    public void setObjectiveValue(double objectiveValue) {
        this.objectiveValue = objectiveValue;
    }

    public int getHardConstraintViolations() {
        return hardConstraintViolations;
    }

    public void setHardConstraintViolations(int hardConstraintViolations) {
        this.hardConstraintViolations = hardConstraintViolations;
    }

    public int getSoftConstraintViolations() {
        return softConstraintViolations;
    }

    public void setSoftConstraintViolations(int softConstraintViolations) {
        this.softConstraintViolations = softConstraintViolations;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public void setFeasible(boolean feasible) {
        this.feasible = feasible;
    }

    // ============================================================================
    // CONSTRUCTION METADATA GETTERS AND SETTERS
    // ============================================================================

    public String getConstructionMethod() {
        return constructionMethod;
    }

    public void setConstructionMethod(String constructionMethod) {
        this.constructionMethod = constructionMethod;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getComputationTime() {
        return computationTime;
    }

    public void setComputationTime(long computationTime) {
        this.computationTime = computationTime;
    }

    public String getSolverVersion() {
        return solverVersion;
    }

    public void setSolverVersion(String solverVersion) {
        this.solverVersion = solverVersion;
    }

    // ============================================================================
    // DATA ACCESS METHODS
    // ============================================================================

    /**
     * Get all monthly assignments
     */
    public Map<String, Map<String, Set<Integer>>> getMonthlyAssignments() {
        Map<String, Map<String, Set<Integer>>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Set<Integer>>> entry : monthlyAssignments.entrySet()) {
            Map<String, Set<Integer>> anaesthetistCopy = new HashMap<>();
            for (Map.Entry<String, Set<Integer>> workstationEntry : entry.getValue().entrySet()) {
                anaesthetistCopy.put(workstationEntry.getKey(), new HashSet<>(workstationEntry.getValue()));
            }
            copy.put(entry.getKey(), anaesthetistCopy);
        }
        return copy;
    }

    /**
     * Get all weekly assignments
     */
    public Map<Integer, Map<String, Map<String, Map<Integer, Boolean>>>> getAllWeeklyAssignments() {
        Map<Integer, Map<String, Map<String, Map<Integer, Boolean>>>> copy = new HashMap<>();
        for (Map.Entry<Integer, Map<String, Map<String, Map<Integer, Boolean>>>> weekEntry : weeklyAssignments
                .entrySet()) {
            Map<String, Map<String, Map<Integer, Boolean>>> weekCopy = new HashMap<>();
            for (Map.Entry<String, Map<String, Map<Integer, Boolean>>> anaesthetistEntry : weekEntry.getValue()
                    .entrySet()) {
                Map<String, Map<Integer, Boolean>> anaesthetistCopy = new HashMap<>();
                for (Map.Entry<String, Map<Integer, Boolean>> workstationEntry : anaesthetistEntry.getValue()
                        .entrySet()) {
                    anaesthetistCopy.put(workstationEntry.getKey(), new HashMap<>(workstationEntry.getValue()));
                }
                weekCopy.put(anaesthetistEntry.getKey(), anaesthetistCopy);
            }
            copy.put(weekEntry.getKey(), weekCopy);
        }
        return copy;
    }

    /**
     * Get all assigned anaesthetists
     */
    public Set<String> getAllAssignedAnaesthetists() {
        Set<String> assigned = new HashSet<>();
        assigned.addAll(monthlyAssignments.keySet());

        for (Map<String, Map<String, Map<Integer, Boolean>>> weekData : weeklyAssignments.values()) {
            assigned.addAll(weekData.keySet());
        }

        return assigned;
    }

    /**
     * Get all assigned workstations
     */
    public Set<String> getAllAssignedWorkstations() {
        Set<String> assigned = new HashSet<>();

        // Monthly workstations
        for (Map<String, Set<Integer>> anaesthetistAssignments : monthlyAssignments.values()) {
            assigned.addAll(anaesthetistAssignments.keySet());
        }

        // Weekly workstations
        for (Map<String, Map<String, Map<Integer, Boolean>>> weekData : weeklyAssignments.values()) {
            for (Map<String, Map<Integer, Boolean>> anaesthetistData : weekData.values()) {
                assigned.addAll(anaesthetistData.keySet());
            }
        }

        return assigned;
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Copy data from another solution
     */
    public void copyFrom(Solution other) {
        if (other == null)
            return;

        // Clear current data
        clear();

        // Copy assignments
        this.monthlyAssignments = new ConcurrentHashMap<>();
        for (Map.Entry<String, Map<String, Set<Integer>>> entry : other.monthlyAssignments.entrySet()) {
            Map<String, Set<Integer>> anaesthetistCopy = new ConcurrentHashMap<>();
            for (Map.Entry<String, Set<Integer>> workstationEntry : entry.getValue().entrySet()) {
                Set<Integer> daysCopy = ConcurrentHashMap.newKeySet();
                daysCopy.addAll(workstationEntry.getValue());
                anaesthetistCopy.put(workstationEntry.getKey(), daysCopy);
            }
            this.monthlyAssignments.put(entry.getKey(), anaesthetistCopy);
        }

        // Deep copy weekly assignments
        this.weeklyAssignments = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, Map<String, Map<String, Map<Integer, Boolean>>>> weekEntry : other.weeklyAssignments
                .entrySet()) {
            Map<String, Map<String, Map<Integer, Boolean>>> weekCopy = new ConcurrentHashMap<>();
            for (Map.Entry<String, Map<String, Map<Integer, Boolean>>> anaesthetistEntry : weekEntry.getValue()
                    .entrySet()) {
                Map<String, Map<Integer, Boolean>> anaesthetistCopy = new ConcurrentHashMap<>();
                for (Map.Entry<String, Map<Integer, Boolean>> workstationEntry : anaesthetistEntry.getValue()
                        .entrySet()) {
                    anaesthetistCopy.put(workstationEntry.getKey(),
                            new ConcurrentHashMap<>(workstationEntry.getValue()));
                }
                weekCopy.put(anaesthetistEntry.getKey(), anaesthetistCopy);
            }
            this.weeklyAssignments.put(weekEntry.getKey(), weekCopy);
        }

        this.restDayRestrictions = new ConcurrentHashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : other.restDayRestrictions.entrySet()) {
            Set<Integer> restrictionsCopy = ConcurrentHashMap.newKeySet();
            restrictionsCopy.addAll(entry.getValue());
            this.restDayRestrictions.put(entry.getKey(), restrictionsCopy);
        }

        // Copy violations and metadata
        this.constraintViolations = new ConcurrentHashMap<>(other.constraintViolations);
        this.statistics = new ConcurrentHashMap<>(other.statistics);
        this.metadata = new ConcurrentHashMap<>(other.metadata);

        // Copy quality metrics
        this.objectiveValue = other.objectiveValue;
        this.hardConstraintViolations = other.hardConstraintViolations;
        this.softConstraintViolations = other.softConstraintViolations;
        this.feasible = other.feasible;

        // Copy construction metadata
        this.constructionMethod = other.constructionMethod;
        this.creationTime = other.creationTime;
        this.computationTime = other.computationTime;
        this.solverVersion = other.solverVersion;
    }

    /**
     * Clear all solution data
     */
    public void clear() {
        monthlyAssignments.clear();
        weeklyAssignments.clear();
        restDayRestrictions.clear();
        constraintViolations.clear();
        statistics.clear();
        metadata.clear();

        objectiveValue = 0.0;
        hardConstraintViolations = 0;
        softConstraintViolations = 0;
        feasible = true;
    }

    /**
     * Check if solution is empty
     */
    public boolean isEmpty() {
        return monthlyAssignments.isEmpty() && weeklyAssignments.isEmpty();
    }

    /**
     * Get solution summary for logging
     */
    public String getSolutionSummary() {
        int totalMonthlyAssignments = monthlyAssignments.values().stream()
                .mapToInt(anaesthetistAssignments -> anaesthetistAssignments.values().stream()
                        .mapToInt(Set::size).sum())
                .sum();

        int totalWeeklyAssignments = 0;
        for (Map<String, Map<String, Map<Integer, Boolean>>> weekData : weeklyAssignments.values()) {
            for (Map<String, Map<Integer, Boolean>> anaesthetistData : weekData.values()) {
                for (Map<Integer, Boolean> workstationData : anaesthetistData.values()) {
                    totalWeeklyAssignments += (int) workstationData.values().stream()
                            .mapToLong(assigned -> Boolean.TRUE.equals(assigned) ? 1 : 0)
                            .sum();
                }
            }
        }

        return String.format("Solution{objective=%.2f, monthly=%d, weekly=%d, hardViol=%d, softViol=%d, feasible=%s}",
                objectiveValue, totalMonthlyAssignments, totalWeeklyAssignments,
                hardConstraintViolations, softConstraintViolations, feasible);
    }

    @Override
    public String toString() {
        return getSolutionSummary();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        Solution solution = (Solution) obj;
        return Double.compare(solution.objectiveValue, objectiveValue) == 0 &&
                hardConstraintViolations == solution.hardConstraintViolations &&
                softConstraintViolations == solution.softConstraintViolations &&
                feasible == solution.feasible &&
                Objects.equals(monthlyAssignments, solution.monthlyAssignments) &&
                Objects.equals(weeklyAssignments, solution.weeklyAssignments) &&
                Objects.equals(constraintViolations, solution.constraintViolations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectiveValue, hardConstraintViolations, softConstraintViolations,
                feasible, monthlyAssignments, weeklyAssignments, constraintViolations);
    }

    // ============================================================================
    // ADDITIONAL UTILITY METHODS FOR DIAGNOSTICS AND EVALUATION
    // ============================================================================

    /**
     * Check if anaesthetist is assigned to any location (monthly or weekly) on
     * specific day
     */
    public boolean isAssignedAnyLocation(String anaesthetistId, int dayNumber) {
        // Check monthly assignments
        if (isAssignedMonthlyAnyLocation(anaesthetistId, dayNumber)) {
            return true;
        }

        // Check weekly assignments for the appropriate week
        int week = ((dayNumber - 1) / 7) + 1;
        if (week >= 1 && week <= 4) {
            return isAssignedWeeklyAnyLocation(anaesthetistId, dayNumber, week);
        }

        return false;
    }

    /**
     * Check if anaesthetist is assigned to morning shift on specific day
     */
    public boolean isAssignedToMorningShift(String anaesthetistId, int dayNumber) {
        int week = ((dayNumber - 1) / 7) + 1;
        if (week < 1 || week > 4)
            return false;

        // Morning weekly workstations: MMAU, MMIU, MWK, OHMAU
        String[] morningWorkstations = { "MMAU", "MMIU", "MWK", "OHMAU" };

        for (String workstation : morningWorkstations) {
            if (isAssignedWeekly(anaesthetistId, workstation, dayNumber, week)) {
                return true;
            }
        }

        // Also check if rest day after SGOT (counts as morning availability)
        if (dayNumber > 1) {
            return isAssignedMonthly(anaesthetistId, "SGOT", dayNumber - 1);
        }

        return false;
    }

    /**
     * Check if anaesthetist is assigned to evening shift on specific day
     */
    public boolean isAssignedToEveningShift(String anaesthetistId, int dayNumber) {
        int week = ((dayNumber - 1) / 7) + 1;
        if (week < 1 || week > 4)
            return false;

        // Evening weekly workstations: EU1, EU2, EWK, OHMIU
        String[] eveningWorkstations = { "EU1", "EU2", "EWK", "OHMIU" };

        for (String workstation : eveningWorkstations) {
            if (isAssignedWeekly(anaesthetistId, workstation, dayNumber, week)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if anaesthetist is assigned to on-call shift on specific day
     */
    public boolean isAssignedToOnCall(String anaesthetistId, int dayNumber) {
        // On-call workstations are monthly workstations
        String[] onCallWorkstations = { "CGOT", "SGOT", "PWOT", "CICU", "SICU", "CCT", "SCT" };

        for (String workstation : onCallWorkstations) {
            if (isAssignedMonthly(anaesthetistId, workstation, dayNumber)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get total number of monthly assignments in the solution
     */
    public int getTotalMonthlyAssignments() {
        return monthlyAssignments.values().stream()
                .mapToInt(anaesthetistAssignments -> anaesthetistAssignments.values().stream()
                        .mapToInt(Set::size).sum())
                .sum();
    }

    /**
     * Get total number of weekly assignments in the solution
     */
    public int getTotalWeeklyAssignments() {
        int total = 0;
        for (Map<String, Map<String, Map<Integer, Boolean>>> weekData : weeklyAssignments.values()) {
            for (Map<String, Map<Integer, Boolean>> anaesthetistData : weekData.values()) {
                for (Map<Integer, Boolean> workstationData : anaesthetistData.values()) {
                    total += (int) workstationData.values().stream()
                            .mapToLong(assigned -> Boolean.TRUE.equals(assigned) ? 1 : 0)
                            .sum();
                }
            }
        }
        return total;
    }

    /**
     * Get summary of assignments per anaesthetist
     */
    public Map<String, Integer> getAssignmentSummaryByAnaesthetist() {
        Map<String, Integer> summary = new HashMap<>();

        // Get all anaesthetists who have any assignments
        Set<String> allAnaesthetists = new HashSet<>();
        allAnaesthetists.addAll(monthlyAssignments.keySet());

        for (Map<String, Map<String, Map<Integer, Boolean>>> weekData : weeklyAssignments.values()) {
            allAnaesthetists.addAll(weekData.keySet());
        }

        // Count total assignments for each
        for (String anaesthetistId : allAnaesthetists) {
            int totalAssignments = 0;

            // Monthly assignments
            totalAssignments += countTotalMonthlyAssignments(anaesthetistId);

            // Weekly assignments
            for (int week = 1; week <= 4; week++) {
                totalAssignments += countTotalWeeklyAssignments(anaesthetistId, week);
            }

            summary.put(anaesthetistId, totalAssignments);
        }

        return summary;
    }

    /**
     * Validate solution structure
     */
    public List<String> validateSolutionStructure() {
        List<String> issues = new ArrayList<>();

        // Check for null data structures
        if (monthlyAssignments == null) {
            issues.add("Monthly assignments map is null");
        }
        if (weeklyAssignments == null) {
            issues.add("Weekly assignments map is null");
        }
        if (constraintViolations == null) {
            issues.add("Constraint violations map is null");
        }

        // Check for negative values
        if (objectiveValue < 0) {
            issues.add("Negative objective value: " + objectiveValue);
        }
        if (hardConstraintViolations < 0) {
            issues.add("Negative hard constraint violations: " + hardConstraintViolations);
        }
        if (softConstraintViolations < 0) {
            issues.add("Negative soft constraint violations: " + softConstraintViolations);
        }

        // Check for inconsistent data
        if (isEmpty() && objectiveValue > 0) {
            issues.add("Empty solution but non-zero objective value");
        }

        // Check monthly assignment consistency
        for (Map.Entry<String, Map<String, Set<Integer>>> anaesthetistEntry : monthlyAssignments.entrySet()) {
            for (Map.Entry<String, Set<Integer>> workstationEntry : anaesthetistEntry.getValue().entrySet()) {
                Set<Integer> days = workstationEntry.getValue();
                if (days == null) {
                    issues.add("Null day set for " + anaesthetistEntry.getKey() + " at " + workstationEntry.getKey());
                } else {
                    for (Integer day : days) {
                        if (day == null || day < 1 || day > 31) {
                            issues.add("Invalid day number: " + day + " for " + anaesthetistEntry.getKey());
                        }
                    }
                }
            }
        }

        // Check weekly assignment consistency
        for (Map.Entry<Integer, Map<String, Map<String, Map<Integer, Boolean>>>> weekEntry : weeklyAssignments
                .entrySet()) {
            Integer week = weekEntry.getKey();
            if (week == null || week < 1 || week > 4) {
                issues.add("Invalid week number: " + week);
            }

            for (Map.Entry<String, Map<String, Map<Integer, Boolean>>> anaesthetistEntry : weekEntry.getValue()
                    .entrySet()) {
                for (Map.Entry<String, Map<Integer, Boolean>> workstationEntry : anaesthetistEntry.getValue()
                        .entrySet()) {
                    Map<Integer, Boolean> dayAssignments = workstationEntry.getValue();
                    if (dayAssignments == null) {
                        issues.add("Null day assignments for " + anaesthetistEntry.getKey() + " at "
                                + workstationEntry.getKey());
                    } else {
                        for (Map.Entry<Integer, Boolean> dayEntry : dayAssignments.entrySet()) {
                            Integer day = dayEntry.getKey();
                            if (day == null || day < 1 || day > 31) {
                                issues.add("Invalid day number in weekly assignments: " + day);
                            }
                        }
                    }
                }
            }
        }

        return issues;
    }

    /**
     * Get solution statistics for reporting
     */
    public Map<String, Object> getSolutionStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("total_monthly_assignments", getTotalMonthlyAssignments());
        stats.put("total_weekly_assignments", getTotalWeeklyAssignments());
        stats.put("unique_anaesthetists_assigned", getAllAssignedAnaesthetists().size());
        stats.put("unique_workstations_assigned", getAllAssignedWorkstations().size());
        stats.put("total_rest_day_restrictions", restDayRestrictions.values().stream().mapToInt(Set::size).sum());
        stats.put("total_constraint_violations",
                constraintViolations.values().stream().mapToInt(Integer::intValue).sum());
        stats.put("is_empty", isEmpty());
        stats.put("has_hard_violations", hasHardConstraintViolations());
        stats.put("objective_value", objectiveValue);
        stats.put("feasible", feasible);
        stats.put("computation_time_ms", computationTime);

        return stats;
    }

}