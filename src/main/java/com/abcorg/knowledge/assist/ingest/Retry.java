package com.abcorg.knowledge.assist.ingest;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.abcorg.knowledge.assist.exception.IngestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Retry {

    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    private Retry() {
    }

    /**
     * Runs the action. If it throws a RuntimeException, waits (exponential backoff + jitter)
     * and tries again, up to maxAttempts total. Use only for calls that are safe to repeat.
     */
    public static <T> T run(String label, int maxAttempts, Duration initialBackoff, Supplier<T> action) {
        long backoffMs = initialBackoff.toMillis();
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts) {
                    throw new IngestException(label + " failed after " + attempt + " attempts", e);
                }
                long sleepMs = backoffMs + ThreadLocalRandom.current().nextLong(backoffMs / 2 + 1);
                log.warn("{} attempt {} failed ({}); retrying in {} ms",
                        label, attempt, e.getMessage(), sleepMs);
                pause(Duration.ofMillis(sleepMs));
                backoffMs *= 2;
            }
        }
    }

    public static void pause(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestException("interrupted while waiting to retry", e);
        }
    }
}
