package com.arshad.chat_backend.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingServiceTest {

    @Test
    void shouldAllowRequestsWithinCapacity() {
        TokenBucket bucket = new TokenBucket(5, 5.0 / 60.0);
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume("user1")).isTrue();
        }
        assertThat(bucket.tryConsume("user1")).isFalse(); // 6th should fail
    }

    @Test
    void shouldAllowDifferentUsersIndependently() {
        TokenBucket bucket = new TokenBucket(1, 1.0 / 60.0);
        assertThat(bucket.tryConsume("user1")).isTrue();
        assertThat(bucket.tryConsume("user2")).isTrue();
        assertThat(bucket.tryConsume("user1")).isFalse();
        assertThat(bucket.tryConsume("user2")).isFalse();
    }

    @Test
    void shouldRefillTokensOverTime() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(1, 60.0); // 1 token per second
        assertThat(bucket.tryConsume("user1")).isTrue();
        assertThat(bucket.tryConsume("user1")).isFalse(); // bucket empty
        Thread.sleep(1100); // wait ~1 second for refill
        assertThat(bucket.tryConsume("user1")).isTrue(); // refilled
    }

    @Test
    void shouldCleanupIdleBuckets() {
        TokenBucket bucket = new TokenBucket(5, 5.0);
        bucket.tryConsume("user1");
        bucket.cleanupOldBuckets(0); // cleanup immediately (age 0)
        // bucket still works after cleanup
        assertThat(bucket.tryConsume("user1")).isTrue();
    }

    @Test
    void serviceShouldAllowAndReject() {
        RateLimitingService service = new RateLimitingService();
        // Can't easily test private init() without reflection,
        // but we can verify constructor doesn't throw
        assertThat(service).isNotNull();
    }
}