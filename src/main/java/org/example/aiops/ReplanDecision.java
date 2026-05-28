package org.example.aiops;

public record ReplanDecision(Action action, ReplanReason reason, String message) {

    public enum Action {
        CONTINUE,
        RETRY,
        REPLAN
    }

    public static ReplanDecision continueExecution() {
        return new ReplanDecision(Action.CONTINUE, null, "continue");
    }

    public static ReplanDecision retry(ReplanReason reason, String message) {
        return new ReplanDecision(Action.RETRY, reason, message);
    }

    public static ReplanDecision replan(ReplanReason reason, String message) {
        return new ReplanDecision(Action.REPLAN, reason, message);
    }
}
