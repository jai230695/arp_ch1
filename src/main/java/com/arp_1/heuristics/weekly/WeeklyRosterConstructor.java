// File: src/main/java/com/arp/heuristics/weekly/WeeklyRosterConstructor.java
package com.arp_1.heuristics.weekly;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.heuristics.base.GreedySelector;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Main coordinator for weekly roster construction
 */
public class WeeklyRosterConstructor {
    private MultiLocationConstructor multiLocationConstructor;
    private PreferenceDrivenConstructor preferenceDrivenConstructor;
    private WeeklyConstraintHandler constraintHandler;
    private GreedySelector selector;

    public WeeklyRosterConstructor(GreedySelector selector) {
        this.selector = selector;
        this.multiLocationConstructor = new MultiLocationConstructor(selector);
        this.preferenceDrivenConstructor = new PreferenceDrivenConstructor(selector);
        this.constraintHandler = new WeeklyConstraintHandler();
    }

    public Solution constructWeeklyRoster(ProblemInstance instance, int week,
            Map<String, Set<Integer>> monthlyConstraints,
            Map<String, List<String>> transitionConstraints) {

        long startTime = System.currentTimeMillis();

        LoggingUtils.logInfo("Starting weekly roster construction for week " + week +
                " with " + selector.getSelectorType());

        // Create weekly solution
        Solution weeklySolution = new Solution();

        // Step 1: Handle multi-location exclusive assignments (MCT, MWK, EWK)
        LoggingUtils.logInfo("Phase 1: Handling exclusive weekly assignments");
        weeklySolution = multiLocationConstructor.constructExclusiveAssignments(
                instance, week, weeklySolution, monthlyConstraints);

        // Step 2: Fill remaining weekly locations with preference consideration
        LoggingUtils.logInfo("Phase 2: Completing weekly roster with preference consideration");
        weeklySolution = preferenceDrivenConstructor.completeWeeklyRoster(
                instance, week, weeklySolution, monthlyConstraints);

        // Step 3: Validate and optimize
        LoggingUtils.logInfo("Phase 3: Validating and optimizing weekly solution");
        weeklySolution = constraintHandler.validateAndOptimize(instance, weeklySolution, week);

        long endTime = System.currentTimeMillis();
        weeklySolution.setComputationTime(endTime - startTime);

        LoggingUtils.logInfo("Weekly roster construction for week " + week +
                " completed in " + (endTime - startTime) + "ms");

        return weeklySolution;
    }

    public void setGreedySelector(GreedySelector newSelector) {
        this.selector = newSelector;
        this.multiLocationConstructor = new MultiLocationConstructor(newSelector);
        this.preferenceDrivenConstructor = new PreferenceDrivenConstructor(newSelector);
    }

    public GreedySelector getGreedySelector() {
        return selector;
    }
}