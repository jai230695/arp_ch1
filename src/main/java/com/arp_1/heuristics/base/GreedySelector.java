// File: src/main/java/com/arp/heuristics/base/GreedySelector.java
package com.arp_1.heuristics.base;

import java.util.List;
import java.util.Map;

/**
 * Interface for selecting anaesthetists during constructive heuristics
 */
public interface GreedySelector {

    /**
     * Select anaesthetist(s) for assignment based on priority calculations
     * 
     * @param candidates List of eligible anaesthetist IDs
     * @param priorities Priority scores for each candidate
     * @param required   Number of anaesthetists needed
     * @return Selected anaesthetist IDs
     */
    List<String> selectAnaesthetists(List<String> candidates,
            Map<String, Double> priorities,
            int required);

    /**
     * Get selector type for logging and analysis
     * 
     * @return Selector type identifier
     */
    String getSelectorType();

    /**
     * Configure the selector with parameters (e.g., randomness level)
     * 
     * @param parameters Configuration parameters
     */
    default void configure(Map<String, Object> parameters) {
        // Default implementation does nothing
    }
}