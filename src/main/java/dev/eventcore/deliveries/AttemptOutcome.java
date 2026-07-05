package dev.eventcore.deliveries;

record AttemptOutcome(Integer statusCode, String error, String responseSnippet, long durationMs) {

    boolean accepted() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
}