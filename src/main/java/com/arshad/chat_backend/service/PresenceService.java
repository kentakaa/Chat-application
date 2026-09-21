package com.arshad.chat_backend.service;

import java.util.Set;

public interface PresenceService {

    

    void setOnline(String userId, String sessionId);
    
    
    void setOffline(String sessionId);
    
    
    boolean isUserOnline(String userId);
    
    
    Set<String> filterOnlineUsers(Set<String> roomMembers);
}
