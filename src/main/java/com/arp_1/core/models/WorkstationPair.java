// File: src/main/java/com/arp/core/models/WorkstationPair.java
package com.arp_1.core.models;

import java.util.Objects;

public class WorkstationPair {
    private String workstation1;
    private String workstation2;

    public WorkstationPair(String workstation1, String workstation2) {
        this.workstation1 = workstation1;
        this.workstation2 = workstation2;
    }

    public boolean contains(String workstation) {
        return workstation1.equals(workstation) || workstation2.equals(workstation);
    }

    public String getOtherWorkstation(String workstation) {
        if (workstation1.equals(workstation))
            return workstation2;
        if (workstation2.equals(workstation))
            return workstation1;
        throw new IllegalArgumentException("Workstation " + workstation + " not in this pair");
    }

    // Getters
    public String getWorkstation1() {
        return workstation1;
    }

    public String getWorkstation2() {
        return workstation2;
    }

    @Override
    public String toString() {
        return String.format("WorkstationPair{w1='%s', w2='%s'}", workstation1, workstation2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        WorkstationPair that = (WorkstationPair) o;
        return Objects.equals(workstation1, that.workstation1) &&
                Objects.equals(workstation2, that.workstation2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workstation1, workstation2);
    }
}
