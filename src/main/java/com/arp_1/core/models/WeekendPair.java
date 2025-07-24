// File: src/main/java/com/arp/core/models/WeekendPair.java
package com.arp_1.core.models;

import java.util.Objects;

public class WeekendPair {
    private int day1;
    private int day2;

    public WeekendPair(int day1, int day2) {
        this.day1 = day1;
        this.day2 = day2;
    }

    public boolean containsDay(int day) {
        return day == day1 || day == day2;
    }

    public int getOtherDay(int day) {
        if (day == day1)
            return day2;
        if (day == day2)
            return day1;
        throw new IllegalArgumentException("Day " + day + " not in this weekend pair");
    }

    // Getters
    public int getDay1() {
        return day1;
    }

    public int getDay2() {
        return day2;
    }

    @Override
    public String toString() {
        return String.format("WeekendPair{day1=%d, day2=%d}", day1, day2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        WeekendPair that = (WeekendPair) o;
        return day1 == that.day1 && day2 == that.day2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(day1, day2);
    }
}