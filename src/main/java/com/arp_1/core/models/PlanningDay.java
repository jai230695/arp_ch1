// File: src/main/java/com/arp/core/models/PlanningDay.java
package com.arp_1.core.models;

import java.util.Objects;

public class PlanningDay {
    private int dayNumber;
    private String dayOfWeek;
    private int week;
    private int actualWeek;
    private String date;
    private boolean isWeekendOrHoliday;
    private boolean isPreHoliday;

    public PlanningDay(int dayNumber, String dayOfWeek, int week, int actualWeek, String date) {
        this.dayNumber = dayNumber;
        this.dayOfWeek = dayOfWeek;
        this.week = week;
        this.actualWeek = actualWeek;
        this.date = date;
        this.isWeekendOrHoliday = false;
        this.isPreHoliday = false;
    }

    public boolean isWeekday() {
        return !isWeekendOrHoliday &&
                !"Saturday".equals(dayOfWeek) &&
                !"Sunday".equals(dayOfWeek);
    }

    public boolean isWeeklyPlanningDay() {
        return true; // All days are potential weekly planning days
    }

    public boolean isMonday() {
        return "Monday".equals(dayOfWeek);
    }

    public boolean isFriday() {
        return "Friday".equals(dayOfWeek);
    }

    public boolean isSaturday() {
        return "Saturday".equals(dayOfWeek);
    }

    public boolean isSunday() {
        return "Sunday".equals(dayOfWeek);
    }

    // Getters and Setters
    public int getDayNumber() {
        return dayNumber;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public int getWeek() {
        return week;
    }

    public int getActualWeek() {
        return actualWeek;
    }

    public String getDate() {
        return date;
    }

    public boolean isWeekendOrHoliday() {
        return isWeekendOrHoliday;
    }

    public boolean isPreHoliday() {
        return isPreHoliday;
    }

    public void setWeekendOrHoliday(boolean weekendOrHoliday) {
        this.isWeekendOrHoliday = weekendOrHoliday;
    }

    public void setPreHoliday(boolean preHoliday) {
        this.isPreHoliday = preHoliday;
    }

    @Override
    public String toString() {
        return String.format("PlanningDay{day=%d, %s, week=%d, date='%s', weekend=%s, preHoliday=%s}",
                dayNumber, dayOfWeek, week, date, isWeekendOrHoliday, isPreHoliday);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PlanningDay that = (PlanningDay) o;
        return dayNumber == that.dayNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dayNumber);
    }
}