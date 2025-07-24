// File: src/main/java/com/arp/core/constraints/ConstraintViolation.java
package com.arp_1.core.constraints;

import java.util.Objects;

public class ConstraintViolation {
    private ConstraintType constraintType;
    private String description;
    private String anaesthetistId;
    private String workstationId;
    private int dayNumber;
    private int violationCount;
    private double penalty;

    public ConstraintViolation(ConstraintType constraintType, String description,
            String anaesthetistId, String workstationId,
            int dayNumber, int violationCount) {
        this.constraintType = constraintType;
        this.description = description;
        this.anaesthetistId = anaesthetistId;
        this.workstationId = workstationId;
        this.dayNumber = dayNumber;
        this.violationCount = violationCount;
        this.penalty = violationCount * constraintType.getDefaultPenaltyWeight();
    }

    public ConstraintViolation(ConstraintType constraintType, String description, int violationCount) {
        this(constraintType, description, null, null, -1, violationCount);
    }

    // Getters
    public ConstraintType getConstraintType() {
        return constraintType;
    }

    public String getDescription() {
        return description;
    }

    public String getAnaesthetistId() {
        return anaesthetistId;
    }

    public String getWorkstationId() {
        return workstationId;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public int getViolationCount() {
        return violationCount;
    }

    public double getPenalty() {
        return penalty;
    }

    public boolean isHardConstraintViolation() {
        return constraintType.isHardConstraint();
    }

    public boolean isSoftConstraintViolation() {
        return constraintType.isSoftConstraint();
    }

    @Override
    public String toString() {
        if (anaesthetistId != null) {
            return String.format("%s: %s (Anaesthetist: %s, Workstation: %s, Day: %d, Count: %d, Penalty: %.0f)",
                    constraintType, description, anaesthetistId, workstationId, dayNumber, violationCount, penalty);
        } else {
            return String.format("%s: %s (Count: %d, Penalty: %.0f)",
                    constraintType, description, violationCount, penalty);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConstraintViolation that = (ConstraintViolation) o;
        return dayNumber == that.dayNumber &&
                violationCount == that.violationCount &&
                constraintType == that.constraintType &&
                Objects.equals(anaesthetistId, that.anaesthetistId) &&
                Objects.equals(workstationId, that.workstationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constraintType, anaesthetistId, workstationId, dayNumber, violationCount);
    }
}