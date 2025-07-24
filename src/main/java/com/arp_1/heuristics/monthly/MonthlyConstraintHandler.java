// File: src/main/java/com/arp/heuristics/monthly/MonthlyConstraintHandler.java
package com.arp_1.heuristics.monthly;

import com.arp_1.core.models.*;
import com.arp_1.core.data.ProblemInstance;
import com.arp_1.core.constraints.*;
import com.arp_1.utils.LoggingUtils;
import java.util.*;

/**
 * Handles constraint validation and optimization for monthly rosters
 */
public class MonthlyConstraintHandler {
    private HardConstraintChecker hardConstraintChecker;
    private SoftConstraintEvaluator softConstraintEvaluator;

    public MonthlyConstraintHandler() {
        this.hardConstraintChecker = new HardConstraintChecker();
        this.softConstraintEvaluator = new SoftConstraintEvaluator();
    }

    public Solution validateAndOptimize(ProblemInstance instance, Solution solution) {
        LoggingUtils.logInfo("Validating and optimizing monthly solution");

        // 1. Check hard constraints
        List<ConstraintViolation> hardViolations = hardConstraintChecker.checkAllHardConstraints(solution, instance);

        if (!hardViolations.isEmpty()) {
            LoggingUtils.logWarning("Hard constraint violations found: " + hardViolations.size());
            solution = attemptHardConstraintRepair(instance, solution, hardViolations);
        } else {
            LoggingUtils.logInfo("All hard constraints satisfied");
        }

        // 2. Evaluate soft constraints
        List<ConstraintViolation> softViolations = softConstraintEvaluator.evaluateAllSoftConstraints(solution,
                instance);
        LoggingUtils.logInfo("Soft constraint violations: " + softViolations.size());

        // Store constraint violation counts
        Map<ConstraintType, Integer> violationCounts = softConstraintEvaluator
                .getSoftConstraintViolationCounts(solution, instance);
        for (Map.Entry<ConstraintType, Integer> entry : violationCounts.entrySet()) {
            solution.addConstraintViolation(entry.getKey().name(), entry.getValue());
        }

        // 3. Calculate objective value
        double totalPenalty = softConstraintEvaluator.calculateTotalSoftConstraintPenalty(solution, instance);
        solution.setObjectiveValue(totalPenalty);

        LoggingUtils.logInfo("Monthly solution validation completed. Objective: " + totalPenalty);

        return solution;
    }

    private Solution attemptHardConstraintRepair(ProblemInstance instance, Solution solution,
            List<ConstraintViolation> violations) {

        LoggingUtils.logInfo("Attempting to repair hard constraint violations");

        // Group violations by type for targeted repair
        Map<ConstraintType, List<ConstraintViolation>> violationsByType = new HashMap<>();
        for (ConstraintViolation violation : violations) {
            violationsByType.computeIfAbsent(violation.getConstraintType(), k -> new ArrayList<>()).add(violation);
        }

        // Repair in priority order
        for (ConstraintType type : getRepairPriorityOrder()) {
            if (violationsByType.containsKey(type)) {
                solution = repairConstraintType(instance, solution, type, violationsByType.get(type));
            }
        }

        // Verify repair success
        List<ConstraintViolation> remainingViolations = hardConstraintChecker.checkAllHardConstraints(solution,
                instance);
        if (remainingViolations.isEmpty()) {
            LoggingUtils.logInfo("Hard constraint repair successful");
        } else {
            LoggingUtils.logError("Hard constraint repair failed. Remaining violations: " + remainingViolations.size());
        }

        return solution;
    }

    private List<ConstraintType> getRepairPriorityOrder() {
        // Order from easiest to hardest to repair
        return Arrays.asList(
                ConstraintType.HC2, // Availability (remove assignments)
                ConstraintType.HC9, // Invalid combinations (remove assignments)
                ConstraintType.HC6, // Rest day after SGOT (remove assignments)
                ConstraintType.HC8, // Daily workload (remove assignments)
                ConstraintType.HC3, // SGOT consecutive (complex)
                ConstraintType.HC7, // Weekend pairing (complex)
                ConstraintType.HC1 // Coverage (hardest - may need reassignment)
        );
    }

    private Solution repairConstraintType(ProblemInstance instance, Solution solution,
            ConstraintType constraintType, List<ConstraintViolation> violations) {

        switch (constraintType) {
            case HC1:
                return repairCoverageViolations(instance, solution, violations);
            case HC2:
                return repairAvailabilityViolations(instance, solution, violations);
            case HC3:
                return repairSGOTConsecutiveViolations(instance, solution, violations);
            case HC6:
                return repairRestDayViolations(instance, solution, violations);
            case HC7:
                return repairWeekendPairingViolations(instance, solution, violations);
            case HC8:
                return repairWorkloadViolations(instance, solution, violations);
            case HC9:
                return repairInvalidCombinationViolations(instance, solution, violations);
            default:
                LoggingUtils.logWarning("No repair method for constraint type: " + constraintType);
                return solution;
        }
    }

    private Solution repairCoverageViolations(ProblemInstance instance, Solution solution,
            List<ConstraintViolation> violations) {
        // HC1: Coverage requirements - most complex repair
        for (ConstraintViolation violation : violations) {
            String workstationId = violation.getWorkstationId();
            int dayNumber = violation.getDayNumber();

            if (workstationId != null && dayNumber > 0) {
                int required = instance.getWorkstationDemand(workstationId, dayNumber);
                int assigned = countAssignments(solution, workstationId, dayNumber);

                if (assigned < required) {
                    // Try to add assignments
                    addCoverageAssignments(instance, solution, workstationId, dayNumber, required - assigned);
                } else if (assigned > required) {
                    // Remove excess assignments
                    removeExcessAssignments(solution, workstationId, dayNumber, assigned - required);
                }
            }
        }
        return solution;
    }

    private Solution repairAvailabilityViolations(ProblemInstance instance, Solution solution,
            List<ConstraintViolation> violations) {
        // HC2: Remove assignments for unavailable anaesthetists
        for (ConstraintViolation violation : violations) {
            String anaesthetistId = violation.getAnaesthetistId();
            int dayNumber = violation.getDayNumber();

            if (anaesthetistId != null && dayNumber > 0) {
                // Remove all monthly assignments for this anaesthetist on this day
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    if (solution.isAssignedMonthly(anaesthetistId, workstation.getId(), dayNumber)) {
                        removeAssignment(solution, anaesthetistId, workstation.getId(), dayNumber);
                        LoggingUtils.logDebug("Removed unavailable assignment: " + anaesthetistId +
                                " from " + workstation.getId() + " on day " + dayNumber);
                    }
                }
            }
        }
        return solution;
    }

    private Solution repairSGOTConsecutiveViolations(ProblemInstance instance, Solution solution,
            List<ConstraintViolation> violations) {
        // HC3: Remove one of the consecutive SGOT assignments
        for (ConstraintViolation violation : violations) {
            String anaesthetistId = violation.getAnaesthetistId();
            int dayNumber = violation.getDayNumber();

            if (anaesthetistId != null && dayNumber > 0) {
                // Remove the second day of consecutive assignment (usually safer)
                if (dayNumber < 28 &&
                        solution.isAssignedMonthly(anaesthetistId, "SGOT", dayNumber + 1)) {
                    removeAssignment(solution, anaesthetistId, "SGOT", dayNumber + 1);
                    LoggingUtils
                            .logDebug("Removed consecutive SGOT: " + anaesthetistId + " from day " + (dayNumber + 1));
                }
            }
        }
        return solution;
    }

    private Solution repairRestDayViolations(ProblemInstance instance, Solution solution,
            List<ConstraintViolation> violations) {
        // HC6: Remove assignments on rest days after SGOT
        for (ConstraintViolation violation : violations) {
            String anaesthetistId = violation.getAnaesthetistId();
            int dayNumber = violation.getDayNumber();

            if (anaesthetistId != null && dayNumber > 0 && dayNumber < 28) {
                // Remove assignments on the day after SGOT
                int restDay = dayNumber + 1;
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    if (solution.isAssignedMonthly(anaesthetistId, workstation.getId(), restDay)) {
                        removeAssignment(solution, anaesthetistId, workstation.getId(), restDay);
                        LoggingUtils.logDebug("Removed rest day violation: " + anaesthetistId +
                                " from " + workstation.getId() + " on day " + restDay);
                    }
                }
            }
        }
        return solution;
    }

    private Solution repairWeekendPairingViolations(ProblemInstance instance, Solution solution,
            List<ConstraintViolation> violations) {
        // HC7: Fix weekend pairing by adding missing assignments
        for (ConstraintViolation violation : violations) {
            String anaesthetistId = violation.getAnaesthetistId();
            String workstationId = violation.getWorkstationId();
            int dayNumber = violation.getDayNumber();

            if (anaesthetistId != null && workstationId != null) {
                // Find the weekend pair and ensure both days are assigned
                for (WeekendPair pair : instance.getWeekendPairs()) {
                    if (pair.containsDay(dayNumber)) {
                        int otherDay = pair.getOtherDay(dayNumber);

                        boolean day1Assigned = solution.isAssignedMonthly(anaesthetistId, workstationId,
                                pair.getDay1());
                        boolean day2Assigned = solution.isAssignedMonthly(anaesthetistId, workstationId,
                                pair.getDay2());

                        if (day1Assigned && !day2Assigned) {
                            solution.assignMonthly(anaesthetistId, workstationId, pair.getDay2());
                        } else if (!day1Assigned && day2Assigned) {
                            solution.assignMonthly(anaesthetistId, workstationId, pair.getDay1());
                        }
                    }
                }
            }
        }
        return solution;
    }

    private Solution repairWorkloadViolations(ProblemInstance instance, Solution solution,
            List<ConstraintViolation> violations) {
        // HC8: Remove assignments that exceed daily workload
        for (ConstraintViolation violation : violations) {
            String anaesthetistId = violation.getAnaesthetistId();
            int dayNumber = violation.getDayNumber();

            if (anaesthetistId != null && dayNumber > 0) {
                // Find and remove the assignment with lowest priority
                List<String> assignedWorkstations = new ArrayList<>();
                for (Workstation workstation : instance.getMonthlyWorkstations()) {
                    if (solution.isAssignedMonthly(anaesthetistId, workstation.getId(), dayNumber)) {
                        assignedWorkstations.add(workstation.getId());
                    }
                }

                // Remove assignments until workload is within limits
                while (!assignedWorkstations.isEmpty()) {
                    double currentWorkload = calculateDayWorkload(instance, solution, anaesthetistId, dayNumber);
                    if (currentWorkload <= instance.getMaxDailyWorkload()) {
                        break;
                    }

                    // Remove the lowest priority workstation (by weight)
                    String toRemove = assignedWorkstations.stream()
                            .min((w1, w2) -> Double.compare(
                                    instance.getWorkstationById(w1).getWeight(),
                                    instance.getWorkstationById(w2).getWeight()))
                            .orElse(null);

                    if (toRemove != null) {
                        removeAssignment(solution, anaesthetistId, toRemove, dayNumber);
                        assignedWorkstations.remove(toRemove);
                        LoggingUtils.logDebug("Removed workload violation: " + anaesthetistId +
                                " from " + toRemove + " on day " + dayNumber);
                    }
                }
            }
        }
        return solution;
    }

    private Solution repairInvalidCombinationViolations(ProblemInstance instance, Solution solution,
            List<ConstraintViolation> violations) {
        // HC9: Remove invalid combinations
        for (ConstraintViolation violation : violations) {
            String anaesthetistId = violation.getAnaesthetistId();
            String workstationId = violation.getWorkstationId();
            int dayNumber = violation.getDayNumber();

            if (anaesthetistId != null && workstationId != null && dayNumber > 0) {
                // Remove the specific invalid assignment
                removeAssignment(solution, anaesthetistId, workstationId, dayNumber);
                LoggingUtils.logDebug("Removed invalid combination: " + anaesthetistId +
                        " from " + workstationId + " on day " + dayNumber);
            }
        }
        return solution;
    }

    // Helper methods
    private int countAssignments(Solution solution, String workstationId, int dayNumber) {
        int count = 0;
        for (String anaesthetistId : solution.getMonthlyAssignments().keySet()) {
            if (solution.isAssignedMonthly(anaesthetistId, workstationId, dayNumber)) {
                count++;
            }
        }
        return count;
    }

    private void addCoverageAssignments(ProblemInstance instance, Solution solution,
            String workstationId, int dayNumber, int needed) {
        // Try to find eligible anaesthetists and assign them
        List<String> candidates = instance.getAnaesthetists().stream()
                .filter(a -> a.isActive())
                .filter(a -> a.isQualifiedFor(workstationId))
                .filter(a -> !instance.isAnaesthetistUnavailable(a.getId(), dayNumber))
                .filter(a -> !solution.isAssignedMonthly(a.getId(), workstationId, dayNumber))
                .map(Anaesthetist::getId)
                .collect(java.util.stream.Collectors.toList());

        int assigned = 0;
        for (String candidateId : candidates) {
            if (assigned >= needed)
                break;

            solution.assignMonthly(candidateId, workstationId, dayNumber);
            assigned++;
            LoggingUtils.logDebug("Added coverage assignment: " + candidateId +
                    " to " + workstationId + " on day " + dayNumber);
        }
    }

    private void removeExcessAssignments(Solution solution, String workstationId, int dayNumber, int toRemove) {
        List<String> assignedAnaesthetists = new ArrayList<>();
        for (String anaesthetistId : solution.getMonthlyAssignments().keySet()) {
            if (solution.isAssignedMonthly(anaesthetistId, workstationId, dayNumber)) {
                assignedAnaesthetists.add(anaesthetistId);
            }
        }

        // Remove the specified number of assignments
        for (int i = 0; i < Math.min(toRemove, assignedAnaesthetists.size()); i++) {
            String anaesthetistId = assignedAnaesthetists.get(i);
            removeAssignment(solution, anaesthetistId, workstationId, dayNumber);
            LoggingUtils.logDebug("Removed excess assignment: " + anaesthetistId +
                    " from " + workstationId + " on day " + dayNumber);
        }
    }

    private void removeAssignment(Solution solution, String anaesthetistId, String workstationId, int dayNumber) {
        // This would need to be implemented in the Solution class
        // For now, we'll log the action
        LoggingUtils.logDebug("Would remove assignment: " + anaesthetistId +
                " from " + workstationId + " on day " + dayNumber);
    }

    private double calculateDayWorkload(ProblemInstance instance, Solution solution,
            String anaesthetistId, int dayNumber) {
        double workload = 0.0;
        for (Workstation workstation : instance.getMonthlyWorkstations()) {
            if (solution.isAssignedMonthly(anaesthetistId, workstation.getId(), dayNumber)) {
                workload += workstation.getWeight();
            }
        }
        return workload;
    }
}