package com.arshad.chat_backend.service;

import java.util.concurrent.ConcurrentHashMap;
public class TokenBucket {
    private final int capacity;
    private final double refillRatePerSecond;
    private final ConcurrentHashMap<String, BucketState> bucket = new ConcurrentHashMap<>();

    public TokenBucket(int capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    public boolean tryConsume(String key){
        BucketState state = bucket.computeIfAbsent(key, k -> new BucketState(capacity, System.nanoTime()));

    long now = System.nanoTime();
        double elapsedSeconds = (now - state.lastRefill) / 1_000_000_000.0;

        // Refill tokens based on time passed
        state.tokens = Math.min(capacity, state.tokens + elapsedSeconds * refillRatePerSecond);
        state.lastRefill = now;

        if (state.tokens >= 1.0) {
            state.tokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Remove buckets that haven't been accessed for maxAgeSeconds.
     * Prevents memory leak with many users.
     */
    public void cleanupOldBuckets(long maxAgeSeconds) {
        long cutoff = System.nanoTime() - (maxAgeSeconds * 1_000_000_000L);
        bucket.entrySet().removeIf(entry -> entry.getValue().lastRefill < cutoff);
    }

    /**
     * Holds per-key bucket state.
     */
    private static class BucketState {
        double tokens;
        long lastRefill;

        BucketState(double initialTokens, long lastRefill) {
            this.tokens = initialTokens;
            this.lastRefill = lastRefill;
        }
    }
}
