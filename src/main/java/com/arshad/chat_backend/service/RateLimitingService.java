package com.arshad.chat_backend.service;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitingService {

    private final ConcurrentHashMap<String, TokenBucket> endpointBuckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        registerBucket("auth:login", 5, 5.0 / 60.0);           // 5 per minute
        registerBucket("auth:register", 5, 5.0 / 60.0);        // 5 per minute
        registerBucket("rooms:create", 10, 10.0 / 60.0);       // 10 per minute
        registerBucket("history:fetch", 30, 30.0 / 60.0);      // 30 per minute
        registerBucket("users:search", 20, 20.0 / 60.0);       // 20 per minute
        registerBucket("ws:message", 20, 20.0 / 60.0);         // 20 per minute (WS frames: CHAT + TYPING)
        registerBucket("rooms:list", 30, 30.0 / 60.0);         // 30 per minute
        registerBucket("rooms:status", 30, 30.0 / 60.0);       // 30 per minute
        registerBucket("rooms:leave", 10, 10.0 / 60.0);        // 10 per minute
        registerBucket("profile:status", 10, 10.0 / 60.0);     // 10 per minute
        registerBucket("default", 60, 60.0 / 60.0);            // 60 per minute fallback

        scheduler.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
    }

    private void registerBucket(String endpoint, int capacity, double refillPerSec) {
        endpointBuckets.put(endpoint, new TokenBucket(capacity, refillPerSec));
    }

    public boolean tryConsume(String endpoint, String userId) {
        String key = endpoint + ":" + userId;
        TokenBucket bucket = endpointBuckets.get(endpoint);
        if (bucket == null) {
            bucket = endpointBuckets.get("default");
        }
        return bucket.tryConsume(key);
    }

    private void cleanup() {
        endpointBuckets.values().forEach(b -> b.cleanupOldBuckets(300));
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}