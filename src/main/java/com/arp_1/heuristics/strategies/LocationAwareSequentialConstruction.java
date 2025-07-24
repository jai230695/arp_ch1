// File: src/main/java/com/arp/heuristics/strategies/LocationAwareSequentialConstruction.java
package com.arp_1.heuristics.strategies;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.heuristics.base.*;
import com.arp_1.heuristics.integration.TemporalCoordinator;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Location-aware sequential construction strategy
 * Constructs solutions by considering workstation-specific requirements and
 * relationships
 */
public class LocationAwareSequentialConstruction implements ConstructiveHeuristic {
    private TemporalCoordinator coordinator;
    private GreedySelector selector;
    private String heuristicName;
    private Map<String, Object> configuration;

    public LocationAwareSequentialConstruction(GreedySelector selector) {
        this.selector = selector;
        this.coordinator = new TemporalCoordinator(selector);
        this.heuristicName = "LOCATION_AWARE_SEQUENTIAL_" + selector.getSelectorType();
        this.configuration = new HashMap<>();
    }

    @Override
    public Solution constructSolution(ProblemInstance instance) {
        long startTime = System.currentTimeMillis();

        LoggingUtils.logSectionHeader("LOCATION-AWARE SEQUENTIAL CONSTRUCTION");
        LoggingUtils.logInfo("Strategy: " + heuristicName);
        LoggingUtils.logInfo("Selector: " + selector);

        try {
            // Reset selector state for fresh run
            selector.configure(configuration);

            // Build integrated solution using temporal coordinator
            Solution solution = coordinator.buildIntegratedSolution(instance);

            // Set construction metadata
            solution.setConstructionMethod(heuristicName);
            solution.setCreationTime(startTime);

            long endTime = System.currentTimeMillis();
            solution.setComputationTime(endTime - startTime);

            LoggingUtils.logSolutionStats("LOCATION_AWARE_COMPLETE",
                    solution.getObjectiveValue(),
                    solution.getHardConstraintViolations(),
                    solution.getSoftConstraintViolations(),
                    endTime - startTime);

            return solution;

        } catch (Exception e) {
            LoggingUtils.logError("Location-aware construction failed: " + e.getMessage(), e);
            throw new RuntimeException("Failed to construct solution", e);
        }
    }

    @Override
    public String getHeuristicName() {
        return heuristicName;
    }

    @Override
    public void configure(Map<String, Object> parameters) {
        this.configuration.putAll(parameters);

        // Apply configuration to selector
        if (selector != null) {
            selector.configure(parameters);
        }

        // Update heuristic name if selector type changed
        if (selector != null) {
            this.heuristicName = "LOCATION_AWARE_SEQUENTIAL_" + selector.getSelectorType();
        }
    }

    @Override
    public void reset() {
        // Reset any internal state
        this.configuration.clear();

        // Reset selector
        if (selector != null) {
            selector.configure(new HashMap<>());
        }
    }

    /**
     * Create a deterministic version of this heuristic
     */
    public static LocationAwareSequentialConstruction createDeterministic() {
        return new LocationAwareSequentialConstruction(new DeterministicSelector());
    }

    /**
     * Create a randomized version of this heuristic with specified parameters
     */
    public static LocationAwareSequentialConstruction createRandomized(long seed, double bias) {
        return new LocationAwareSequentialConstruction(new RandomizedSelector(seed, bias));
    }

    /**
     * Change the selector used by this heuristic
     */
    public void setSelector(GreedySelector newSelector) {
        this.selector = newSelector;
        this.coordinator.setGreedySelector(newSelector);
        this.heuristicName = "LOCATION_AWARE_SEQUENTIAL_" + newSelector.getSelectorType();
    }

    public GreedySelector getSelector() {
        return selector;
    }

    /**
     * Get detailed configuration information
     */
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>(this.configuration);
        config.put("selector_type", selector.getSelectorType());
        config.put("heuristic_name", heuristicName);
        return config;
    }

    @Override
    public String toString() {
        return String.format("LocationAwareSequentialConstruction{name='%s', selector=%s, config=%s}",
                heuristicName, selector, configuration);
    }
}