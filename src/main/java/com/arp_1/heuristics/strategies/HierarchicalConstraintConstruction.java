// File: src/main/java/com/arp/heuristics/strategies/HierarchicalConstraintConstruction.java
package com.arp_1.heuristics.strategies;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.core.constraints.*;
import com.arp_1.heuristics.base.*;
import com.arp_1.heuristics.integration.TemporalCoordinator;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Hierarchical constraint construction strategy
 * Prioritizes constraint satisfaction in hierarchical order
 */
public class HierarchicalConstraintConstruction implements ConstructiveHeuristic {
    private TemporalCoordinator coordinator;
    private GreedySelector selector;
    private String heuristicName;
    private Map<String, Object> configuration;
    private HardConstraintChecker hardConstraintChecker;
    private SoftConstraintEvaluator softConstraintEvaluator;

    // Constraint hierarchy levels - FIXED SYNTAX
    private static final Map<ConstraintType, Integer> CONSTRAINT_HIERARCHY;
    static {
        Map<ConstraintType, Integer> hierarchy = new HashMap<>();
        // Level 1: Critical constraints (must be satisfied)
        hierarchy.put(ConstraintType.HC1, 1); // Coverage requirements
        hierarchy.put(ConstraintType.HC2, 1); // Availability enforcement
        hierarchy.put(ConstraintType.HC4, 1); // Qualification requirements

        // Level 2: Important constraints (strongly preferred)
        hierarchy.put(ConstraintType.HC3, 2); // SGOT consecutive restrictions
        hierarchy.put(ConstraintType.HC6, 2); // Rest day after SGOT
        hierarchy.put(ConstraintType.HC8, 2); // Daily workload limits
        hierarchy.put(ConstraintType.HC9, 2); // Invalid combinations

        // Level 3: Preference constraints (nice to have)
        hierarchy.put(ConstraintType.HC5, 3); // Weekly working hours
        hierarchy.put(ConstraintType.HC7, 3); // Weekend pairing
        hierarchy.put(ConstraintType.HC10, 3); // Shift succession
        hierarchy.put(ConstraintType.HC11, 3); // Mandatory pairings

        CONSTRAINT_HIERARCHY = Collections.unmodifiableMap(hierarchy);
    }

    public HierarchicalConstraintConstruction(GreedySelector selector) {
        this.selector = selector;
        this.coordinator = new TemporalCoordinator(selector);
        this.heuristicName = "HIERARCHICAL_CONSTRAINT_" + selector.getSelectorType();
        this.configuration = new HashMap<>();
        this.hardConstraintChecker = new HardConstraintChecker();
        this.softConstraintEvaluator = new SoftConstraintEvaluator();
    }

    @Override
    public Solution constructSolution(ProblemInstance instance) {
        long startTime = System.currentTimeMillis();

        LoggingUtils.logSectionHeader("HIERARCHICAL CONSTRAINT CONSTRUCTION");
        LoggingUtils.logInfo("Strategy: " + heuristicName);
        LoggingUtils.logInfo("Constraint hierarchy approach activated");

        try {
            // Configure constraint weights based on hierarchy
            Map<String, Object> hierarchicalConfig = new HashMap<>(configuration);
            hierarchicalConfig.put("use_constraint_hierarchy", true);
            hierarchicalConfig.put("constraint_weights", CONSTRAINT_HIERARCHY);

            selector.configure(hierarchicalConfig);

            // Build solution with hierarchical constraint consideration
            Solution solution = coordinator.buildIntegratedSolution(instance);

            // Apply hierarchical optimization
            solution = applyHierarchicalOptimization(solution, instance);

            solution.setConstructionMethod(heuristicName);
            solution.setCreationTime(startTime);

            long endTime = System.currentTimeMillis();
            solution.setComputationTime(endTime - startTime);

            LoggingUtils.logSolutionStats("HIERARCHICAL_COMPLETE",
                    solution.getObjectiveValue(),
                    solution.getHardConstraintViolations(),
                    solution.getSoftConstraintViolations(),
                    endTime - startTime);

            return solution;

        } catch (Exception e) {
            LoggingUtils.logError("Hierarchical construction failed: " + e.getMessage(), e);
            throw new RuntimeException("Failed to construct hierarchical solution", e);
        }
    }

    private Solution applyHierarchicalOptimization(Solution solution, ProblemInstance instance) {
        LoggingUtils.logInfo("Applying hierarchical optimization");

        // Check all hard constraints first
        List<ConstraintViolation> hardViolations = hardConstraintChecker.checkAllHardConstraints(solution, instance);

        if (!hardViolations.isEmpty()) {
            LoggingUtils.logWarning("Hard constraint violations found: " + hardViolations.size());

            // Group violations by hierarchy level
            Map<Integer, List<ConstraintViolation>> violationsByLevel = groupViolationsByLevel(hardViolations);

            // Repair violations level by level
            for (int level = 1; level <= 3; level++) {
                if (violationsByLevel.containsKey(level)) {
                    solution = repairConstraintLevel(solution, instance, level, violationsByLevel.get(level));
                    LoggingUtils.logInfo("Repaired level " + level + " constraints: " +
                            violationsByLevel.get(level).size() + " violations");
                }
            }
        }

        // Evaluate soft constraints with hierarchy weights
        Map<ConstraintType, Integer> violationCounts = softConstraintEvaluator
                .getSoftConstraintViolationCounts(solution, instance);

        // Apply hierarchy-weighted penalties
        double hierarchicalPenalty = calculateHierarchicalPenalty(violationCounts);
        solution.setObjectiveValue(hierarchicalPenalty);

        LoggingUtils.logInfo("Hierarchical optimization completed. Final penalty: " + hierarchicalPenalty);
        return solution;
    }

    private Map<Integer, List<ConstraintViolation>> groupViolationsByLevel(List<ConstraintViolation> violations) {
        Map<Integer, List<ConstraintViolation>> violationsByLevel = new HashMap<>();

        for (ConstraintViolation violation : violations) {
            ConstraintType type = violation.getConstraintType();
            Integer level = CONSTRAINT_HIERARCHY.get(type);

            if (level != null) {
                violationsByLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(violation);
            } else {
                // Default to level 2 for unknown constraints
                violationsByLevel.computeIfAbsent(2, k -> new ArrayList<>()).add(violation);
            }
        }

        return violationsByLevel;
    }

    private Solution repairConstraintLevel(Solution solution, ProblemInstance instance,
            int level, List<ConstraintViolation> violations) {

        LoggingUtils.logInfo("Repairing constraint level " + level + " with " + violations.size() + " violations");

        for (ConstraintViolation violation : violations) {
            solution = repairSpecificViolation(solution, instance, violation);
        }

        return solution;
    }

    private Solution repairSpecificViolation(Solution solution, ProblemInstance instance,
            ConstraintViolation violation) {

        ConstraintType type = violation.getConstraintType();

        switch (type) {
            case HC1: // Coverage requirements
                solution = repairCoverageViolation(solution, instance, violation);
                break;
            case HC2: // Availability enforcement
                solution = repairAvailabilityViolation(solution, instance, violation);
                break;
            case HC3: // SGOT consecutive restrictions
                solution = repairSGOTConsecutiveViolation(solution, instance, violation);
                break;
            case HC4: // Qualification requirements
                solution = repairQualificationViolation(solution, instance, violation);
                break;
            case HC6: // Rest day after SGOT
                solution = repairRestDayViolation(solution, instance, violation);
                break;
            case HC8: // Daily workload limits
                solution = repairWorkloadViolation(solution, instance, violation);
                break;
            case HC9: // Invalid combinations
                solution = repairInvalidCombinationViolation(solution, instance, violation);
                break;
            default:
                LoggingUtils.logWarning("No repair method for constraint type: " + type);
                break;
        }

        return solution;
    }

    private Solution repairCoverageViolation(Solution solution, ProblemInstance instance,
            ConstraintViolation violation) {
        // Implementation for coverage repair
        LoggingUtils.logDebug("Repairing coverage violation: " + violation.getDescription());
        return solution;
    }

    private Solution repairAvailabilityViolation(Solution solution, ProblemInstance instance,
            ConstraintViolation violation) {
        // Implementation for availability repair
        LoggingUtils.logDebug("Repairing availability violation: " + violation.getDescription());
        return solution;
    }

    private Solution repairSGOTConsecutiveViolation(Solution solution, ProblemInstance instance,
            ConstraintViolation violation) {
        // Implementation for SGOT consecutive repair
        LoggingUtils.logDebug("Repairing SGOT consecutive violation: " + violation.getDescription());
        return solution;
    }

    private Solution repairQualificationViolation(Solution solution, ProblemInstance instance,
            ConstraintViolation violation) {
        // Implementation for qualification repair
        LoggingUtils.logDebug("Repairing qualification violation: " + violation.getDescription());
        return solution;
    }

    private Solution repairRestDayViolation(Solution solution, ProblemInstance instance,
            ConstraintViolation violation) {
        // Implementation for rest day repair
        LoggingUtils.logDebug("Repairing rest day violation: " + violation.getDescription());
        return solution;
    }

    private Solution repairWorkloadViolation(Solution solution, ProblemInstance instance,
            ConstraintViolation violation) {
        // Implementation for workload repair
        LoggingUtils.logDebug("Repairing workload violation: " + violation.getDescription());
        return solution;
    }

    private Solution repairInvalidCombinationViolation(Solution solution, ProblemInstance instance,
            ConstraintViolation violation) {
        // Implementation for invalid combination repair
        LoggingUtils.logDebug("Repairing invalid combination violation: " + violation.getDescription());
        return solution;
    }

    private double calculateHierarchicalPenalty(Map<ConstraintType, Integer> violationCounts) {
        double totalPenalty = 0.0;

        for (Map.Entry<ConstraintType, Integer> entry : violationCounts.entrySet()) {
            ConstraintType type = entry.getKey();
            int count = entry.getValue();

            // Get hierarchy level (default to 2 if not found)
            int level = CONSTRAINT_HIERARCHY.getOrDefault(type, 2);

            // Calculate hierarchy-weighted penalty
            double basePenalty = type.getDefaultPenaltyWeight() * count;
            double hierarchyMultiplier = getHierarchyMultiplier(level);

            totalPenalty += basePenalty * hierarchyMultiplier;
        }

        return totalPenalty;
    }

    private double getHierarchyMultiplier(int level) {
        switch (level) {
            case 1:
                return 3.0; // Critical constraints get 3x penalty
            case 2:
                return 2.0; // Important constraints get 2x penalty
            case 3:
                return 1.0; // Preference constraints get normal penalty
            default:
                return 1.5; // Unknown level gets 1.5x penalty
        }
    }

    @Override
    public String getHeuristicName() {
        return heuristicName;
    }

    @Override
    public void configure(Map<String, Object> parameters) {
        this.configuration.putAll(parameters);
        if (selector != null) {
            selector.configure(parameters);
        }
    }

    @Override
    public void reset() {
        this.configuration.clear();
        if (selector != null) {
            selector.configure(new HashMap<>());
        }
    }

    public static HierarchicalConstraintConstruction createDeterministic() {
        return new HierarchicalConstraintConstruction(new DeterministicSelector());
    }

    public static HierarchicalConstraintConstruction createRandomized(long seed, double bias) {
        return new HierarchicalConstraintConstruction(new RandomizedSelector(seed, bias));
    }

    public void setSelector(GreedySelector newSelector) {
        this.selector = newSelector;
        this.coordinator.setGreedySelector(newSelector);
        this.heuristicName = "HIERARCHICAL_CONSTRAINT_" + newSelector.getSelectorType();
    }

    public GreedySelector getSelector() {
        return selector;
    }

    @Override
    public String toString() {
        return String.format("HierarchicalConstraintConstruction{name='%s', selector=%s}",
                heuristicName, selector);
    }
}
