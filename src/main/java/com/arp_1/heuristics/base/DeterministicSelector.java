// File: src/main/java/com/arp/heuristics/base/DeterministicSelector.java
package com.arp_1.heuristics.base;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic greedy selector - always selects highest priority candidates
 */
public class DeterministicSelector implements GreedySelector {

    @Override
    public List<String> selectAnaesthetists(List<String> candidates,
            Map<String, Double> priorities,
            int required) {
        if (candidates.isEmpty() || required <= 0) {
            return new ArrayList<>();
        }

        // Pure greedy: select highest priority candidates
        return candidates.stream()
                .sorted((a1, a2) -> Double.compare(
                        priorities.getOrDefault(a2, 0.0),
                        priorities.getOrDefault(a1, 0.0)))
                .limit(required)
                .collect(Collectors.toList());
    }

    @Override
    public String getSelectorType() {
        return "DETERMINISTIC";
    }

    @Override
    public String toString() {
        return "DeterministicSelector{type=GREEDY}";
    }
}