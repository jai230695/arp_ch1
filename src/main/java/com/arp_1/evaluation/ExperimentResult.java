// File: src/main/java/com/arp/evaluation/ExperimentResult.java
package com.arp_1.evaluation;

import com.arp_1.core.models.Solution;
import java.util.Objects;

public class ExperimentResult {
    private String configurationName;
    private int runNumber;
    private Solution solution;
    private long computationTime;
    private long timestamp;

    public ExperimentResult(String configurationName, int runNumber, Solution solution, long computationTime) {
        this.configurationName = configurationName;
        this.runNumber = runNumber;
        this.solution = solution;
        this.computationTime = computationTime;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public String getConfigurationName() {
        return configurationName;
    }

    public int getRunNumber() {
        return runNumber;
    }

    public Solution getSolution() {
        return solution;
    }

    public long getComputationTime() {
        return computationTime;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getObjectiveValue() {
        return solution.getObjectiveValue();
    }

    public boolean isValid() {
        return solution != null && !solution.hasHardConstraintViolations();
    }

    @Override
    public String toString() {
        return String.format("ExperimentResult{config='%s', run=%d, objective=%.2f, time=%dms}",
                configurationName, runNumber, getObjectiveValue(), computationTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ExperimentResult that = (ExperimentResult) o;
        return runNumber == that.runNumber &&
                Objects.equals(configurationName, that.configurationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configurationName, runNumber);
    }
}