package com.arshad.chat_backend.service;

import java.util.Set;

public interface PresenceService {

    

    void setOnline(String userId, String sessionId);
    
    // Jab user disconnect ho, use clean karo
    void setOffline(String sessionId);
    
    // Kisi specific user ka real-time status check karne ke liye
    boolean isUserOnline(String userId);
    
    // Ek group/room ke andar kaun-kaun se members online hain unhe filter karne ke liye
    Set<String> filterOnlineUsers(Set<String> roomMembers);
}
