package com.abcorg.knowledge.assist.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryTest {

    @Test
    void retriesUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        String result = Retry.run("flaky", 4, Duration.ofMillis(1), e -> true, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryWhenNotRetryable() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> Retry.run("bad", 4, Duration.ofMillis(1), e -> false, () -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("permanent");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
}
