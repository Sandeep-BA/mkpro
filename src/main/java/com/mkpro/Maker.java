package com.mkpro;

import com.mkpro.models.Goal;
import java.util.List;

public class Maker {

    public static void validateGoals(CentralMemory memory, String projectPath) {
        // Validation logic moved to areGoalsPending. Printing removed.
    }

    public static boolean areGoalsPending(CentralMemory memory, String projectPath) {
        if (memory == null || projectPath == null) {
            return false;
        }

        List<Goal> goals = memory.getGoals(projectPath);
        
        if (goals == null || goals.isEmpty()) {
            return false;
        }

        for (Goal goal : goals) {
            if (goal.getStatus() != Goal.Status.COMPLETED) {
                return true;
            }
        }

        return false;
    }
}
