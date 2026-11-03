package com.abcorg.knowledge.assist.chunking;

/**
 * Rough token counter: about 4 characters per token for English prose.
 * Good enough for budgets and reports. Not good enough for billing.
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() + 3) / 4; // round up
    }
}
