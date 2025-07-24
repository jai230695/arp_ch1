package com.arp_1.core.models;

import java.util.Objects;

public class Workstation {
    private String id;
    private String name;
    private String type; // Major, Minor
    private String shift; // PassiveOnCall, ActiveOnCall, PrivateOnCall, Morning, Evening, OfficeHours
    private int maxWorkingHour;
    private int restDay;
    private int startTime;
    private int endTime;
    private double weight;
    private String rosterType; // Monthly, Weekly

    public Workstation(String id, String name, String type, String shift,
            int maxWorkingHour, int restDay, int startTime,
            int endTime, double weight, String rosterType) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.shift = shift;
        this.maxWorkingHour = maxWorkingHour;
        this.restDay = restDay;
        this.startTime = startTime;
        this.endTime = endTime;
        this.weight = weight;
        this.rosterType = rosterType;
    }

    public boolean isMonthlyRoster() {
        return "Monthly".equals(rosterType);
    }

    public boolean isWeeklyRoster() {
        return "Weekly".equals(rosterType);
    }

    public boolean isMajor() {
        return "Major".equals(type);
    }

    public boolean isMinor() {
        return "Minor".equals(type);
    }

    public boolean isSGOT() {
        return "SGOT".equals(id);
    }

    public boolean isCICU() {
        return "CICU".equals(id);
    }

    public boolean isSICU() {
        return "SICU".equals(id);
    }

    public boolean isOnCallShift() {
        return shift.contains("OnCall");
    }

    public boolean isMorningShift() {
        return "Morning".equals(shift);
    }

    public boolean isEveningShift() {
        return "Evening".equals(shift);
    }

    public boolean isOfficeHoursShift() {
        return "OfficeHours".equals(shift);
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getShift() {
        return shift;
    }

    public int getMaxWorkingHour() {
        return maxWorkingHour;
    }

    public int getRestDay() {
        return restDay;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public double getWeight() {
        return weight;
    }

    public String getRosterType() {
        return rosterType;
    }

    @Override
    public String toString() {
        return String.format("Workstation{id='%s', name='%s', type='%s', shift='%s', rosterType='%s'}",
                id, name, type, shift, rosterType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Workstation that = (Workstation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
