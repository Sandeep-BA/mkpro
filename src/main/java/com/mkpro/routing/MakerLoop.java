package com.mkpro.routing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MakerLoop is the goal-driven supervisor that ensures tasks are completed, not just attempted.
 * 
 * It sits between user input and the Coordinator, injecting per-turn stimulus
 * that drives agents forward through multi-step workflows until verified completion.
 * 
 * Key behaviors:
 * - Creates goals from user input
 * - Generates stimulus context injected before each Coordinator turn
 * - Observes turn results (agent, tools, success/fail)
 * - Decides: CONTINUE / RETRY / ESCALATE / COMPLETE
 * - Learns from completed sequences to improve future decisions
 */
public class MakerLoop {

    private static final String ANSI_PURPLE = "\u001b[35m";
    private static final String ANSI_GREEN = "\u001b[32m";
    private static final String ANSI_YELLOW = "\u001b[33m";
    private static final String ANSI_RED = "\u001b[31m";
    private static final String ANSI_RESET = "\u001b[0m";

    private final MarkovRouter router;
    private final IntentClassifier classifier;
    private final Map<String, MakerState> activeGoals = new ConcurrentHashMap<>();
    private MakerState currentGoal;

    // Event bus (set after construction)
    private volatile com.mkpro.events.MkProEventBus eventBus;

    // Knowledge components (set after construction, optional)
    private volatile com.mkpro.knowledge.KnowledgeScheduler knowledgeScheduler;
    private volatile com.mkpro.knowledge.KnowledgeStore knowledgeStore;
    private volatile com.mkpro.knowledge.TopicIndex topicIndex;
    private volatile java.util.function.Function<String, String> llmCallback; // prompt → response

    // Configuration
    private double autoCompleteThreshold = 0.75;
    private double escalateThreshold = 0.40;
    private int maxRetries = 3;
    private static final int MAX_KNOWLEDGE_ACQUISITIONS_PER_GOAL = 2;
    private static final int KNOWLEDGE_WAIT_SECONDS = 45;

    public MakerLoop(MarkovRouter router) {
        this.router = router;
        this.classifier = new IntentClassifier();
    }

    public void setEventBus(com.mkpro.events.MkProEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Wire knowledge components for proactive gap detection.
     */
    public void setKnowledgeComponents(com.mkpro.knowledge.KnowledgeScheduler scheduler,
                                       com.mkpro.knowledge.KnowledgeStore store,
                                       com.mkpro.knowledge.TopicIndex index) {
        this.knowledgeScheduler = scheduler;
        this.knowledgeStore = store;
        this.topicIndex = index;
    }

    /**
     * Set LLM callback for generating knowledge topic suggestions.
     * Function takes a prompt string and returns LLM response.
     */
    public void setLlmCallback(java.util.function.Function<String, String> callback) {
        this.llmCallback = callback;
    }

    /**
     * Called when user sends a new message.
     * Creates a new goal or continues the existing one.
     */
    public MakerState onUserInput(String input) {
        IntentClassifier.TaskCategory category = classifier.classify(input);
        
        // If there's an active goal, check if this is a continuation
        if (currentGoal != null && currentGoal.getPhase() == MakerState.GoalPhase.ACTIVE) {
            // Continue if: same category, generic follow-up, or a continuation phrase
            if (category == currentGoal.getCategory() 
                || category == IntentClassifier.TaskCategory.GENERAL
                || isContinuationPhrase(input)) {
                return currentGoal;
            }
            // Different category — close old goal, start new one
            learnFromCompletion(true); // Assume the old goal was done enough
        }

        // Create a new goal
        currentGoal = new MakerState(input, category, maxRetries);
        activeGoals.put(currentGoal.getGoalId(), currentGoal);
        if (eventBus != null) eventBus.emit(com.mkpro.events.MkProEvent.makerGoal(truncate(input, 60) + " (category: " + category + ")"));
        else System.out.println(ANSI_PURPLE + "  [Maker] New goal: \"" + truncate(input, 60) + "\" (category: " + category + ")" + ANSI_RESET);

        // Proactive knowledge acquisition: check if the goal domain has coverage
        checkAndAcquireKnowledge(input);

        return currentGoal;
    }

    /**
     * Detect if a user message is a continuation/follow-up to the existing goal.
     * Short imperative phrases like "execute goals", "continue", "next step" etc.
     */
    private boolean isContinuationPhrase(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase().trim();
        
        // Very short inputs are typically follow-ups
        if (lower.split("\\s+").length <= 4) {
            String[] continuationWords = {
                "continue", "proceed", "go ahead", "next", "do it", "execute",
                "run it", "start", "begin", "carry on", "keep going", "resume",
                "yes", "yep", "yeah", "sure", "ok", "okay", "do that", "go on",
                "finish", "complete", "do them", "do this", "do all"
            };
            for (String phrase : continuationWords) {
                if (lower.contains(phrase)) return true;
            }
        }
        return false;
    }

    /**
     * Called BEFORE sending to Coordinator.
     * Returns stimulus text to inject, or null if no active goal.
     */
    public String generatePreTurnStimulus() {
        if (currentGoal == null || currentGoal.getPhase() == MakerState.GoalPhase.DONE) {
            return null;
        }

        // Only inject stimulus after the first turn (let the first delegation happen naturally)
        if (currentGoal.getTurnCount() == 0) {
            return null;
        }

        return currentGoal.generateStimulus(router);
    }

    /**
     * Generate a message for auto-continuation.
     * This allows the Maker to drive the loop without user input.
     */
    public String generateAutoContMessage() {
        if (currentGoal == null || currentGoal.getPhase() == MakerState.GoalPhase.DONE) {
            return null;
        }
        
        // Wrap-up: ask Coordinator to summarize and close
        if (currentGoal.getPhase() == MakerState.GoalPhase.WRAPPING_UP) {
            return "Summarize what has been accomplished so far for the goal: \"" + 
                truncate(currentGoal.getGoalDescription(), 80) + 
                "\". List what was completed and what remains (if anything). Then conclude.";
        }
        
        // Redirect: force delegation to a different agent
        String redirectTarget = currentGoal.getRedirectTarget();
        if (redirectTarget != null) {
            currentGoal.setRedirectTarget(null); // Consume it
            return "Delegate to " + redirectTarget + ": " + currentGoal.getGoalDescription() + 
                "\n\n(Previous approach with " + currentGoal.getLastAgent() + " was not making progress. Try a different strategy.)";
        }
        
        // Normal continue
        String stimulus = currentGoal.generateStimulus(router);
        String base = "Continue with the current task. " + 
            (stimulus != null ? stimulus : "Proceed to the next step.");
        
        return base;
    }

    /**
     * Get the current active goal (for TerminalUI to check state).
     */
    public MakerState getCurrentGoal() {
        return currentGoal;
    }

    /**
     * Called AFTER Coordinator responds.
     * Observes what happened and decides the next action.
     * 
     * @param agentUsed Which agent handled the task
     * @param toolsInvoked List of tools that were called
     * @param success Whether the turn succeeded
     * @param response The full response text (for heuristic completion detection)
     * @return The recommended action
     */
    public MarkovRouter.MakerAction onTurnComplete(String agentUsed, List<String> toolsInvoked, boolean success, String response) {
        if (currentGoal == null) return MarkovRouter.MakerAction.CONTINUE;

        // If we were wrapping up, this turn is the summary — mark complete
        if (currentGoal.getPhase() == MakerState.GoalPhase.WRAPPING_UP) {
            currentGoal.setPhase(MakerState.GoalPhase.DONE);
            learnFromCompletion(true);
            if (eventBus != null) eventBus.emit(com.mkpro.events.MkProEvent.makerComplete(truncate(currentGoal.getGoalDescription(), 60) + " (" + currentGoal.getTurnCount() + " turns)"));
            else System.out.println(ANSI_GREEN + "  ✓ [Maker] Goal wrapped up: \"" + truncate(currentGoal.getGoalDescription(), 60) + "\" (" + currentGoal.getTurnCount() + " turns)" + ANSI_RESET);
            return MarkovRouter.MakerAction.COMPLETE;
        }

        // Record this turn
        currentGoal.recordTurn(agentUsed, toolsInvoked, success);

        // Show turn progress with reasoning
        double completionProb = router.predictCompletion(currentGoal.getCategory(), currentGoal.getToolSequence());
        
        // Heuristic boost: if response contains completion language, boost probability
        if (response != null && completionProb < 0.75) {
            completionProb = Math.max(completionProb, detectCompletionFromResponse(response));
        }
        
        int avgTurns = router.getAvgTurns(currentGoal.getCategory());
        boolean stalled = router.isStalled(currentGoal.getCategory(), currentGoal.getTurnCount());
        
        System.out.println(ANSI_PURPLE + "  [Maker] Turn " + currentGoal.getTurnCount() + 
            "/~" + avgTurns + " | Agent: " + agentUsed + 
            " | " + (success ? "✓" : "✗") +
            " | Completion: " + (int)(completionProb * 100) + "%" +
            (stalled ? " | ⚠ STALLED" : "") + ANSI_RESET);
        if (eventBus != null) eventBus.emit(com.mkpro.events.MkProEvent.makerThought("Turn " + currentGoal.getTurnCount() + "/~" + avgTurns,
            "Agent: " + agentUsed + " | " + (success ? "✓" : "✗") + " | Completion: " + (int)(completionProb * 100) + "%" + (stalled ? " | STALLED" : "")));

        // Show thought process
        StringBuilder thought = new StringBuilder();
        thought.append("  [Maker] Thinking: ");
        if (completionProb >= 0.75) {
            thought.append("Tool pattern matches known completion (").append((int)(completionProb * 100)).append("%) → COMPLETE");
        } else if (!success) {
            thought.append("Last step failed → RETRY (attempt ").append(currentGoal.getRetryCount()).append("/").append(maxRetries).append(")");
        } else if (stalled) {
            // Try redirecting to a different agent before wrapping up
            java.util.Set<String> triedAgents = new java.util.HashSet<>(currentGoal.getAgentSequence());
            String alternative = router.routeExcluding(currentGoal.getCategory(), triedAgents);
            if (alternative != null && currentGoal.getRedirectCount() < 2) {
                thought.append("Stuck with ").append(triedAgents).append(". Redirecting to ").append(alternative).append(".");
                currentGoal.incrementRedirectCount();
                currentGoal.setRedirectTarget(alternative);
                stalled = false; // Don't escalate — we're redirecting
            } else {
                thought.append("All alternatives exhausted → ESCALATE (wrap up)");
            }
        } else {
            // Check stall prediction before deciding CONTINUE
            double stallProb = router.predictStall(currentGoal.getCategory(), currentGoal.getAgentSequence());
            if (stallProb >= 0.6) {
                java.util.Set<String> triedAgents = new java.util.HashSet<>(currentGoal.getAgentSequence());
                String alternative = router.routeExcluding(currentGoal.getCategory(), triedAgents);
                if (alternative != null && currentGoal.getRedirectCount() < 2) {
                    thought.append("⚡ Stall predicted (").append((int)(stallProb * 100)).append("%). Redirecting to ").append(alternative).append(".");
                    currentGoal.incrementRedirectCount();
                    currentGoal.setRedirectTarget(alternative);
                    stalled = false; // Don't escalate, redirect instead
                } else {
                    thought.append("⚡ Stall predicted, no alternatives → ESCALATE");
                    stalled = true;
                }
            } else {
                MarkovRouter.RoutingDecision next = router.route(currentGoal.getCategory(), agentUsed);
                thought.append("Progress OK. Next likely: ").append(next.agent).append(" (").append((int)(next.confidence * 100)).append("%) → CONTINUE");
            }
        }
        System.out.println(ANSI_PURPLE + thought + ANSI_RESET);

        // Get Markov recommendation
        MarkovRouter.MakerAction action = router.recommendAction(
            currentGoal.getCategory(),
            currentGoal.getTurnCount(),
            agentUsed,
            success,
            currentGoal.getToolSequence()
        );

        // Override: if completion detected (by model OR heuristic), force COMPLETE
        if (completionProb >= 0.75 && success) {
            action = MarkovRouter.MakerAction.COMPLETE;
        }

        // Override: if redirect target is set, force CONTINUE (auto-continue will handle it)
        if (currentGoal.getRedirectTarget() != null) {
            action = MarkovRouter.MakerAction.CONTINUE;
        }

        // Override: if max retries exceeded, escalate
        if (action == MarkovRouter.MakerAction.RETRY && currentGoal.isMaxRetriesExceeded()) {
            action = MarkovRouter.MakerAction.ESCALATE;
        }

        // Execute the action
        executeAction(action);

        return action;
    }

    /**
     * Execute the Maker's decision.
     */
    private void executeAction(MarkovRouter.MakerAction action) {
        switch (action) {
            case COMPLETE:
                currentGoal.setPhase(MakerState.GoalPhase.DONE);
                learnFromCompletion(true);
                System.out.println(ANSI_GREEN + "  ✓ [Maker] Goal complete: \"" + 
                    truncate(currentGoal.getGoalDescription(), 60) + "\" (" + 
                    currentGoal.getTurnCount() + " turns)" + ANSI_RESET);
                if (eventBus != null) eventBus.emit(com.mkpro.events.MkProEvent.makerComplete(
                    truncate(currentGoal.getGoalDescription(), 60) + " (" + currentGoal.getTurnCount() + " turns)"));
                break;

            case RETRY:
                currentGoal.setPhase(MakerState.GoalPhase.RETRYING);
                System.out.println(ANSI_YELLOW + "  ↻ [Maker] Retrying (attempt " + 
                    currentGoal.getRetryCount() + "/" + maxRetries + ")" + ANSI_RESET);
                break;

            case ESCALATE:
                // Instead of stopping, ask Coordinator to summarize and wrap up
                System.out.println(ANSI_YELLOW + "  ⚠ [Maker] Stall detected (" + currentGoal.getTurnCount() + 
                    " turns). Asking Coordinator to summarize and conclude." + ANSI_RESET);
                // Don't mark as failed — let one more turn happen with wrap-up stimulus
                currentGoal.setPhase(MakerState.GoalPhase.WRAPPING_UP);
                break;

            case CONTINUE:
                // Normal flow — goal still active
                break;
        }
    }

    /**
     * Generate a retry stimulus — tells the Coordinator what failed and what to try.
     */
    public String generateRetryStimulus() {
        if (currentGoal == null || currentGoal.getPhase() != MakerState.GoalPhase.RETRYING) {
            return null;
        }

        StringBuilder sb = new StringBuilder("[MAKER RETRY]\n");
        sb.append("The previous step FAILED. ");
        sb.append("Goal: \"").append(truncate(currentGoal.getGoalDescription(), 80)).append("\"\n");
        sb.append("Failed agent: ").append(currentGoal.getLastAgent()).append("\n");
        sb.append("Retry count: ").append(currentGoal.getRetryCount()).append("/").append(maxRetries).append("\n");

        // Suggest a different agent if available
        MarkovRouter.RoutingDecision alt = router.route(currentGoal.getCategory(), currentGoal.getLastAgent());
        if (alt.confidence > 0.4 && !alt.agent.equals(currentGoal.getLastAgent())) {
            sb.append("Suggestion: Try ").append(alt.agent).append(" instead.\n");
        } else {
            sb.append("Suggestion: Try a different approach with the same agent.\n");
        }

        // Reset phase to active for next turn
        currentGoal.setPhase(MakerState.GoalPhase.ACTIVE);
        return sb.toString();
    }

    /**
     * Mark the current goal as done by user confirmation.
     */
    public void markComplete() {
        if (currentGoal != null) {
            currentGoal.setPhase(MakerState.GoalPhase.DONE);
            learnFromCompletion(true);
        }
    }

    /**
     * Mark current goal as abandoned/failed.
     */
    public void markFailed() {
        if (currentGoal != null) {
            currentGoal.setPhase(MakerState.GoalPhase.DONE);
            learnFromCompletion(false);
        }
    }

    /**
     * Learn from a completed goal sequence.
     */
    private void learnFromCompletion(boolean success) {
        if (currentGoal == null) return;
        router.recordCompletion(
            currentGoal.getCategory(),
            currentGoal.getToolSequence(),
            success,
            currentGoal.getTurnCount()
        );
        // Record stall pattern on failure/escalation for future prediction
        if (!success && currentGoal.getAgentSequence().size() >= 2) {
            router.recordStall(currentGoal.getCategory(), currentGoal.getAgentSequence());
        }
    }

    /**
     * Check if there's an active goal in progress.
     */
    public boolean hasActiveGoal() {
        return currentGoal != null && 
               currentGoal.getPhase() != MakerState.GoalPhase.DONE &&
               currentGoal.getPhase() != MakerState.GoalPhase.ESCALATED;
    }

    /**
    /**
     * Reset — clear current goal (user starts fresh topic).
     */
    public void reset() {
        if (currentGoal != null && currentGoal.getPhase() != MakerState.GoalPhase.DONE) {
            learnFromCompletion(false); // Abandoned = failed
        }
        currentGoal = null;
    }

    // Configuration
    public void setAutoCompleteThreshold(double t) { this.autoCompleteThreshold = t; }
    public void setEscalateThreshold(double t) { this.escalateThreshold = t; }
    public void setMaxRetries(int r) { this.maxRetries = r; }

    /**
     * Backward-compatible overload without response text.
     */
    public MarkovRouter.MakerAction onTurnComplete(String agentUsed, List<String> toolsInvoked, boolean success) {
        return onTurnComplete(agentUsed, toolsInvoked, success, null);
    }

    /**
     * Heuristic completion detection from response text.
     * Looks for language indicating the task is done.
     * Returns a probability (0.0 - 1.0).
     */
    private double detectCompletionFromResponse(String response) {
        if (response == null || response.isEmpty()) return 0.0;
        
        String lower = response.toLowerCase();
        int signals = 0;
        
        // Strong completion signals
        String[] strongSignals = {
            "has been verified", "has been completed", "has been confirmed",
            "is complete", "is done", "is finished", "is ready",
            "successfully", "task complete", "all done",
            "here's the result", "here are the results",
            "confirmed that", "everything is working",
            "operation completed", "operation successful"
        };
        
        for (String signal : strongSignals) {
            if (lower.contains(signal)) signals += 2;
        }
        
        // Moderate completion signals
        String[] moderateSignals = {
            "summary", "in conclusion", "to summarize",
            "the output shows", "as you can see",
            "no issues found", "no errors",
            "working correctly", "functioning properly"
        };
        
        for (String signal : moderateSignals) {
            if (lower.contains(signal)) signals += 1;
        }
        
        // Cap at 1.0, threshold at 3 signal points for high confidence
        return Math.min(1.0, signals / 3.0);
    }

    /**
     * Check if the goal domain has knowledge coverage. If not, ask LLM to suggest
     * a topic and schedule immediate acquisition.
     */
    private void checkAndAcquireKnowledge(String goalText) {
        // Guard: need all components + non-trivial goal
        if (knowledgeScheduler == null || topicIndex == null || llmCallback == null) return;
        if (goalText == null || goalText.split("\\s+").length < 4) return;

        try {
            // Check existing coverage
            List<com.mkpro.knowledge.TopicIndex.SearchResult> results = topicIndex.search(goalText, 3);
            if (!results.isEmpty() && results.get(0).getScore() > 0.3) {
                // Already have relevant knowledge
                return;
            }

            // Ask LLM to suggest a knowledge topic
            String prompt = buildKnowledgeSuggestionPrompt(goalText);
            String response = llmCallback.apply(prompt);
            if (response == null || response.isBlank()) return;

            // Parse suggestion
            com.mkpro.knowledge.TopicConfig topic = parseKnowledgeSuggestion(response);
            if (topic == null) return;

            // Schedule acquisition
            boolean added = knowledgeScheduler.addTopic(topic);
            if (!added) return; // Already exists

            String msg = "Acquiring knowledge: " + topic.getName() + "...";
            if (eventBus != null) eventBus.emit(com.mkpro.events.MkProEvent.system(msg));
            else System.out.println(ANSI_PURPLE + "  [Maker] " + msg + ANSI_RESET);

            // Wait for first fetch (best-effort, non-blocking beyond timeout)
            boolean ready = waitForTopicReady(topic.getName(), KNOWLEDGE_WAIT_SECONDS);

            if (ready) {
                String readyMsg = "Knowledge acquired: " + topic.getName();
                if (eventBus != null) eventBus.emit(com.mkpro.events.MkProEvent.system(readyMsg));
                else System.out.println(ANSI_GREEN + "  [Maker] " + readyMsg + ANSI_RESET);
            } else {
                String timeoutMsg = "Knowledge fetch timed out for " + topic.getName() + " — proceeding without.";
                if (eventBus != null) eventBus.emit(com.mkpro.events.MkProEvent.system(timeoutMsg));
                else System.out.println(ANSI_YELLOW + "  [Maker] " + timeoutMsg + ANSI_RESET);
            }

        } catch (Exception e) {
            // Non-fatal — proceed without knowledge
            System.out.println(ANSI_YELLOW + "  [Maker] Knowledge gap check failed: " + e.getMessage() + ANSI_RESET);
        }
    }

    private String buildKnowledgeSuggestionPrompt(String goalText) {
        return "A developer wants to accomplish this goal:\n\"" + goalText + "\"\n\n" +
            "Determine if this goal requires specialized knowledge that an AI agent might not have " +
            "(e.g., specific API docs, framework guides, best practices from official sources).\n\n" +
            "If YES, respond with EXACTLY this format (no extra text):\n" +
            "TOPIC:<lowercase-hyphen-name>\n" +
            "URL:<official documentation or reference URL>\n" +
            "FOCUS:<what to analyze, max 1 sentence>\n\n" +
            "If NO (the goal is simple, generic, or already well-known), respond with just: NONE\n\n" +
            "Rules:\n" +
            "- Only suggest official documentation URLs (docs.*, developer.*, official guides)\n" +
            "- Topic name should be specific (e.g., 'k8s-hpa-autoscaling' not 'kubernetes')\n" +
            "- Only suggest if the knowledge would meaningfully help the task";
    }

    /**
     * Parse LLM response into a TopicConfig.
     * Expected format:
     *   TOPIC:name
     *   URL:https://...
     *   FOCUS:instruction
     */
    private com.mkpro.knowledge.TopicConfig parseKnowledgeSuggestion(String response) {
        if (response.trim().equalsIgnoreCase("NONE")) return null;

        String name = null, url = null, focus = null;

        for (String line : response.split("\n")) {
            line = line.trim();
            if (line.startsWith("TOPIC:")) name = line.substring(6).trim();
            else if (line.startsWith("URL:")) url = line.substring(4).trim();
            else if (line.startsWith("FOCUS:")) focus = line.substring(6).trim();
        }

        if (name == null || name.isBlank() || url == null || url.isBlank()) return null;

        com.mkpro.knowledge.TopicConfig topic = new com.mkpro.knowledge.TopicConfig();
        topic.setName(name.toLowerCase().replaceAll("[^a-z0-9-]", "-"));
        topic.setTitle(name);
        topic.setSources(java.util.List.of(url));
        topic.setInstruction(focus != null ? focus : "Analyze for practical implementation guidance.");
        topic.setRefreshIntervalMinutes(360); // Low refresh — just need initial fetch
        return topic;
    }

    /**
     * Wait for a topic report to be ready in the store.
     * @return true if ready within timeout
     */
    private boolean waitForTopicReady(String topicName, int maxWaitSeconds) {
        for (int i = 0; i < maxWaitSeconds; i++) {
            com.mkpro.knowledge.TopicReport report = knowledgeStore.getReport(topicName);
            if (report != null && report.getSummary() != null && !report.getSummary().isBlank()) {
                return true;
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
