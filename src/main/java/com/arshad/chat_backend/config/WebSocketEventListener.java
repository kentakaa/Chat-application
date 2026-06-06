package com.arshad.chat_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;



@Component
@Slf4j
public class WebSocketEventListener {

    // 🟢 Jab user app open karta hai aur WebSocket se judta hai
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        log.info("NEW SESSION: A user connected to the WebSockets.");
        // Yahan aage chalkar hum user ko 'ONLINE' mark karenge (RAM/Redis mein)
    }

    // 🔴 Jab user tab close karta hai, browser band karta hai, ya net chala jata hai
   @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // 1. Pehle attributes ka dabba nikalo (Bina .get() lagaye)
        java.util.Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        // 2. SAFE NULL CHECK: Agar dabba null nahi hai, tabhi aage badho
        if (sessionAttributes != null) {
            String username = (String) sessionAttributes.get("username");

            if (username != null) {
                log.info("SESSION DESTROYED: User '{}' disconnected from WebSockets.", username);
                
                // Yahan aage chalkar hum user ko 'OFFLINE' mark karenge
            }
        }
    }
}
