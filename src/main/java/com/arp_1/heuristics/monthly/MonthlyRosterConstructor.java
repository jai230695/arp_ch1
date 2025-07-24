// File: src/main/java/com/arp/heuristics/monthly/MonthlyRosterConstructor.java
package com.arp_1.heuristics.monthly;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.heuristics.base.GreedySelector;
import com.arp_1.utils.LoggingUtils;

/**
 * Main coordinator for monthly roster construction
 */
public class MonthlyRosterConstructor {
    private SGOTAwareConstructor sgotConstructor;
    private FairnessDrivenConstructor fairnessConstructor;
    private MonthlyConstraintHandler constraintHandler;
    private GreedySelector selector;

    public MonthlyRosterConstructor(GreedySelector selector) {
        this.selector = selector;
        this.sgotConstructor = new SGOTAwareConstructor(selector);
        this.fairnessConstructor = new FairnessDrivenConstructor(selector);
        this.constraintHandler = new MonthlyConstraintHandler();
    }

    public Solution constructMonthlyRoster(ProblemInstance instance) {
        long startTime = System.currentTimeMillis();

        LoggingUtils.logInfo("Starting monthly roster construction with " + selector.getSelectorType());

        // Step 1: Handle SGOT assignments first (most constrained)
        LoggingUtils.logInfo("Phase 1: Constructing SGOT schedule");
        Solution partialSolution = sgotConstructor.constructSGOTSchedule(instance);
        LoggingUtils.logInfo("SGOT assignments completed: " +
                partialSolution.countMonthlyAssignments("", "SGOT") + " total assignments");

        // Step 2: Fill remaining on-call locations with fairness consideration
        LoggingUtils.logInfo("Phase 2: Completing monthly roster with fairness consideration");
        Solution completeSolution = fairnessConstructor.completeMonthlyRoster(instance, partialSolution);

        // Step 3: Validate and optimize
        LoggingUtils.logInfo("Phase 3: Validating and optimizing solution");
        completeSolution = constraintHandler.validateAndOptimize(instance, completeSolution);

        long endTime = System.currentTimeMillis();
        completeSolution.setComputationTime(endTime - startTime);

        LoggingUtils.logInfo("Monthly roster construction completed in " +
                (endTime - startTime) + "ms");

        return completeSolution;
    }

    public void setGreedySelector(GreedySelector newSelector) {
        this.selector = newSelector;
        this.sgotConstructor = new SGOTAwareConstructor(newSelector);
        this.fairnessConstructor = new FairnessDrivenConstructor(newSelector);
    }

    public GreedySelector getGreedySelector() {
        return selector;
    }
}
