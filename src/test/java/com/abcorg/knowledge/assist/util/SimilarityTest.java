package com.abcorg.knowledge.assist.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

public class SimilarityTest {

    private static final float[] CAT = {0.9f, 0.1f};
    private static final float[] DOG = {0.8f, 0.3f};
    private static final float[] CAR = {0.1f, 0.9f};

    @Test
    void catIsCloseToDog() {
        // Hand calculation in section 3.4 gives about 0.969
        assertThat(Similarity.cosine(CAT, DOG)).isCloseTo(0.969, offset(0.001));
    }

    @Test
    void catIsFarFromCar() {
        // Hand calculation gives about 0.220
        assertThat(Similarity.cosine(CAT, CAR)).isCloseTo(0.220, offset(0.001));
    }

    @Test
    void identicalVectorsScoreOne() {
        assertThat(Similarity.cosine(CAT, CAT)).isCloseTo(1.0, offset(1e-6));
    }

    @Test
    void lengthDoesNotChangeCosine() {
        // Same direction, twice as long: cosine must still be 1.0
        float[] doubled = {1.8f, 0.2f};
        assertThat(Similarity.cosine(CAT, doubled)).isCloseTo(1.0, offset(1e-6));
    }
}