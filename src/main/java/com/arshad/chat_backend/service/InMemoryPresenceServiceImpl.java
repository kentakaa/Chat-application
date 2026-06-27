package com.arshad.chat_backend.service;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
@Service
public class InMemoryPresenceServiceImpl implements PresenceService {
    // Thread-safe in-memory state tracking
    private final ConcurrentHashMap<String, String> userIdToSession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionIdToUserId = new ConcurrentHashMap<>();

    @Override
    public void setOnline(String userId, String sessionId) {
        userIdToSession.put(userId, sessionId);
        sessionIdToUserId.put(sessionId, userId);
        System.out.println("🟢 [Presence] User " + userId + " is now ONLINE.");
    }

    @Override
    public void setOffline(String sessionId) {
        String userId = sessionIdToUserId.remove(sessionId);
        if (userId != null) {
            userIdToSession.remove(userId);
            System.out.println("🔴 [Presence] User " + userId + " went OFFLINE.");
        }
    }

    @Override
    public boolean isUserOnline(String userId) {
        return userIdToSession.containsKey(userId);
    }

    @Override
    public Set<String> filterOnlineUsers(Set<String> roomMembers) {
        // Sirf un members ko return karo jo active map mein hain
        return roomMembers.stream()
                .filter(userIdToSession::containsKey)
                .collect(Collectors.toSet());
    }
    
}
