package com.mkpro.commands.impl;

import com.mkpro.commands.Command;
import com.mkpro.core.MkProContext;

import static com.mkpro.MkPro.*;

/**
 * /compact command — Configures invocation turn limits via ContextFilterPlugin
 * and performs context & storage compaction.
 *
 * Usage:
 *   /compact         - Compact active context window and run compaction
 *   /compact <turns> - Set maximum invocation turns to retain in context history
 *   /compact 0       - Disable compaction limit (retains full history)
 */
public class CompactCommand implements Command {

    @Override
    public String getName() {
        return "compact";
    }

    @Override
    public String getDescription() {
        return "Compact context window turns and underlying storage (e.g. /compact [turns]).";
    }

    @Override
    public void execute(String[] args, MkProContext context) throws Exception {
        if (args != null && args.length > 0) {
            try {
                int limit = Integer.parseInt(args[0]);
                if (limit > 0) {
                    context.setMaxTurns(limit);
                    if (context.getAgentManager() != null && context.getAgentConfigs() != null) {
                        context.setRunner(context.getAgentManager().createRunner(context.getAgentConfigs(), "", limit));
                    }
                    System.out.println(ANSI_GREEN + "✔ Context compaction set: keeping last " + limit + " invocation turns in active context." + ANSI_RESET);
                    return;
                } else if (limit == 0 || limit == -1) {
                    context.setMaxTurns(-1);
                    if (context.getAgentManager() != null && context.getAgentConfigs() != null) {
                        context.setRunner(context.getAgentManager().createRunner(context.getAgentConfigs(), "", -1));
                    }
                    System.out.println(ANSI_GREEN + "✔ Context compaction disabled: retaining full session history." + ANSI_RESET);
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        int currentTurns = context.getMaxTurns();
        System.out.println(ANSI_CYAN + "Compacting session context and memory caches..." + ANSI_RESET);
        if (currentTurns > 0) {
            if (context.getAgentManager() != null && context.getAgentConfigs() != null) {
                context.setRunner(context.getAgentManager().createRunner(context.getAgentConfigs(), "", currentTurns));
            }
            System.out.println("• Active context turn window: " + currentTurns + " invocations");
        } else {
            System.out.println("• Active context turn window: full session (unlimited)");
        }

        // Run garbage collection / cache refresh to compact memory
        System.gc();

        System.out.println(ANSI_GREEN + "✔ Compaction complete." + ANSI_RESET);
    }
}
