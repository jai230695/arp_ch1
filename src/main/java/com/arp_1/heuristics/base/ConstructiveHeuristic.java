// File: src/main/java/com/arp/heuristics/base/ConstructiveHeuristic.java
package com.arp_1.heuristics.base;

import com.arp_1.core.models.Solution;
import com.arp_1.core.data.ProblemInstance;

/**
 * Base interface for all constructive heuristics approaches
 */
public interface ConstructiveHeuristic {

    /**
     * Construct a complete solution for the given problem instance
     * 
     * @param instance The problem instance to solve
     * @return A complete solution
     */
    Solution constructSolution(ProblemInstance instance);

    /**
     * Get the name/type of this heuristic for logging and analysis
     * 
     * @return Heuristic identifier
     */
    String getHeuristicName();

    /**
     * Configure the heuristic with specific parameters
     * 
     * @param parameters Configuration parameters
     */
    default void configure(java.util.Map<String, Object> parameters) {
        // Default implementation does nothing
    }

    /**
     * Reset any internal state for a fresh run
     */
    default void reset() {
        // Default implementation does nothing
    }
}