package com.abcorg.knowledge.assist.util;

/**
 * Plain vector math. No framework, no API calls.
 * Each method is small on purpose so you can check it by hand.
 */
public final class Similarity {

    private Similarity() {}

    /** Dot product: multiply matching positions and add. */
    public static double dot(float[] a, float[] b) {
        requireSameLength(a, b);
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    /** Length of a vector: sqrt(a1² + a2² + ...). */
    public static double norm(float[] v) {
        return Math.sqrt(dot(v, v));
    }

    /**
     * Cosine similarity: dot(a, b) / (|a| * |b|).
     * Ranges from -1 to 1 for real vectors. Returns 0 if either vector is all zeros.
     */
    public static double cosine(float[] a, float[] b) {
        double denominator = norm(a) * norm(b);
        if (denominator == 0.0) {
            return 0.0;
        }
        return dot(a, b) / denominator;
    }

    private static void requireSameLength(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vectors must have the same dimension: " + a.length + " vs " + b.length);
        }
    }
}
