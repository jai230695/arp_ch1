// File: src/main/java/com/arp/core/models/Assignment.java
package com.arp_1.core.models;

import java.util.Objects;

public class Assignment {
    private String anaesthetistId;
    private String workstationId;
    private int dayNumber;
    private int week;
    private boolean isMonthlyAssignment;

    public Assignment(String anaesthetistId, String workstationId, int dayNumber,
            int week, boolean isMonthlyAssignment) {
        this.anaesthetistId = anaesthetistId;
        this.workstationId = workstationId;
        this.dayNumber = dayNumber;
        this.week = week;
        this.isMonthlyAssignment = isMonthlyAssignment;
    }

    // Getters
    public String getAnaesthetistId() {
        return anaesthetistId;
    }

    public String getWorkstationId() {
        return workstationId;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public int getWeek() {
        return week;
    }

    public boolean isMonthlyAssignment() {
        return isMonthlyAssignment;
    }

    @Override
    public String toString() {
        return String.format("Assignment{anaesthetist='%s', workstation='%s', day=%d, week=%d, monthly=%s}",
                anaesthetistId, workstationId, dayNumber, week, isMonthlyAssignment);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Assignment that = (Assignment) o;
        return dayNumber == that.dayNumber &&
                week == that.week &&
                isMonthlyAssignment == that.isMonthlyAssignment &&
                Objects.equals(anaesthetistId, that.anaesthetistId) &&
                Objects.equals(workstationId, that.workstationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anaesthetistId, workstationId, dayNumber, week, isMonthlyAssignment);
    }
}
