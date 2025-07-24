// File: src/main/java/com/arp/heuristics/base/RandomizedSelector.java
package com.arp_1.heuristics.base;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Randomized greedy selector with bias control
 */
public class RandomizedSelector implements GreedySelector {
    private Random random;
    private double selectionBias; // 0.0 = pure random, 1.0 = deterministic
    private long seed;

    public RandomizedSelector(long seed, double selectionBias) {
        this.seed = seed;
        this.random = new Random(seed);
        this.selectionBias = Math.max(0.0, Math.min(1.0, selectionBias));
    }

    @Override
    public List<String> selectAnaesthetists(List<String> candidates,
            Map<String, Double> priorities,
            int required) {
        if (candidates.isEmpty() || required <= 0) {
            return new ArrayList<>();
        }

        List<String> selected = new ArrayList<>();
        List<String> availableCandidates = new ArrayList<>(candidates);

        for (int i = 0; i < required && !availableCandidates.isEmpty(); i++) {
            String selectedCandidate = selectSingleCandidate(availableCandidates, priorities);
            if (selectedCandidate != null) {
                selected.add(selectedCandidate);
                availableCandidates.remove(selectedCandidate);
            }
        }

        return selected;
    }

    private String selectSingleCandidate(List<String> candidates,
            Map<String, Double> priorities) {
        if (candidates.isEmpty())
            return null;
        if (candidates.size() == 1)
            return candidates.get(0);

        // Create weighted selection based on priorities
        List<WeightedCandidate> weightedCandidates = createWeightedList(candidates, priorities);

        // Apply selection bias
        if (random.nextDouble() < selectionBias) {
            // Bias towards higher priority (top 20% of candidates)
            int topCandidates = Math.max(1, (int) Math.ceil(candidates.size() * 0.2));
            weightedCandidates = weightedCandidates.subList(0, Math.min(topCandidates, weightedCandidates.size()));
        }

        // Randomized selection from weighted candidates
        return performWeightedRandomSelection(weightedCandidates);
    }

    private List<WeightedCandidate> createWeightedList(List<String> candidates,
            Map<String, Double> priorities) {
        List<WeightedCandidate> weighted = new ArrayList<>();

        // Find min and max priorities for normalization
        double minPriority = priorities.values().stream()
                .mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxPriority = priorities.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(1.0);
        double range = maxPriority - minPriority;

        for (String candidate : candidates) {
            double priority = priorities.getOrDefault(candidate, 0.0);
            // Normalize to [0.1, 1.0] to ensure all candidates have some selection
            // probability
            double normalizedWeight = range > 0 ? 0.1 + 0.9 * (priority - minPriority) / range : 1.0;
            weighted.add(new WeightedCandidate(candidate, normalizedWeight));
        }

        // Sort by weight (highest first)
        weighted.sort((a, b) -> Double.compare(b.getWeight(), a.getWeight()));

        return weighted;
    }

    private String performWeightedRandomSelection(List<WeightedCandidate> candidates) {
        if (candidates.isEmpty())
            return null;
        if (candidates.size() == 1)
            return candidates.get(0).getCandidateId();

        double totalWeight = candidates.stream().mapToDouble(WeightedCandidate::getWeight).sum();
        double randomValue = random.nextDouble() * totalWeight;

        double cumulativeWeight = 0.0;
        for (WeightedCandidate candidate : candidates) {
            cumulativeWeight += candidate.getWeight();
            if (randomValue <= cumulativeWeight) {
                return candidate.getCandidateId();
            }
        }

        // Fallback: return last candidate
        return candidates.get(candidates.size() - 1).getCandidateId();
    }

    @Override
    public String getSelectorType() {
        return "RANDOMIZED_BIAS_" + selectionBias;
    }

    @Override
    public void configure(Map<String, Object> parameters) {
        if (parameters.containsKey("seed")) {
            this.seed = ((Number) parameters.get("seed")).longValue();
            this.random = new Random(this.seed);
        }
        if (parameters.containsKey("bias")) {
            this.selectionBias = Math.max(0.0, Math.min(1.0,
                    ((Number) parameters.get("bias")).doubleValue()));
        }
    }

    public double getSelectionBias() {
        return selectionBias;
    }

    public long getSeed() {
        return seed;
    }

    @Override
    public String toString() {
        return String.format("RandomizedSelector{bias=%.2f, seed=%d}", selectionBias, seed);
    }

    // Inner class for weighted selection
    private static class WeightedCandidate {
        private final String candidateId;
        private final double weight;

        public WeightedCandidate(String candidateId, double weight) {
            this.candidateId = candidateId;
            this.weight = weight;
        }

        public String getCandidateId() {
            return candidateId;
        }

        public double getWeight() {
            return weight;
        }

        @Override
        public String toString() {
            return String.format("WeightedCandidate{id='%s', weight=%.3f}", candidateId, weight);
        }
    }
}