// File: src/main/java/com/arp/core/constraints/ConstraintType.java
package com.arp_1.core.constraints;

public enum ConstraintType {
    // Hard Constraints
    HC1("Coverage Requirements"),
    HC2("Availability Enforcement"),
    HC3("SGOT Consecutive Restrictions"),
    HC4("Qualification Requirements"),
    HC5("Weekly Working Hours"),
    HC6("Rest Day After SGOT"),
    HC7("Weekend Pairing"),
    HC8("Daily Workload Limits"),
    HC9("Invalid Combinations"),
    HC10("Shift Succession"),
    HC11("Mandatory Pairings"),

    // Soft Constraints
    SC1("Rest Day Compliance"),
    SC2("No Call Requests"),
    SC3("Shift Requests"),
    SC4("Preferred Pairings"),
    SC5("Fair Workload Distribution"),
    SC6("Fair Weekend Distribution"),
    SC7("Fair Pre-Holiday Distribution"),
    SC8("Preference Accommodation"),
    SC9("Consecutive Day Assignments"),
    SC10("Undesired Combinations");

    private final String description;

    ConstraintType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHardConstraint() {
        return name().startsWith("HC");
    }

    public boolean isSoftConstraint() {
        return name().startsWith("SC");
    }

    public int getDefaultPenaltyWeight() {
        switch (this) {
            case SC1:
                return 10; // Rest days
            case SC2:
                return 5; // No call requests
            case SC3:
                return 30; // Shift requests (highest)
            case SC4:
                return 8; // Preferred pairings
            case SC5:
                return 10; // Fair distribution
            case SC6:
                return 10; // Fair weekend
            case SC7:
                return 3; // Fair pre-holiday
            case SC8:
                return 8; // Preferences
            case SC9:
                return 8; // Consecutive days
            case SC10:
                return 8; // Undesired combinations
            default:
                return 0; // Hard constraints have no penalty
        }
    }
}