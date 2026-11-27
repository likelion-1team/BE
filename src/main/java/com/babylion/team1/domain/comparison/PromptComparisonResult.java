package com.babylion.team1.domain.comparison;

public record PromptComparisonResult(Run baseline, Run improved) {

    public record Configuration(boolean configured, String model) {
    }

    public enum Status { COMPLETED, INCOMPLETE, REFUSED, FAILED }

    public record Usage(int inputTokens, int outputTokens, int totalTokens, Integer cachedInputTokens) {
    }

    public record ProviderIds(String requestId, String responseId) {
    }

    public record Run(Status status, String model, String output, Usage usage, String message, ProviderIds providerIds) {
        static Run failed(String message) {
            return Run.failed(message, null);
        }

        static Run failed(String message, ProviderIds providerIds) {
            return new Run(Status.FAILED, null, null, null, message, providerIds);
        }
    }
}
