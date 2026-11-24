package com.arshad.chat_backend.config;

import com.arshad.chat_backend.config.ChatWebSocketHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LogoutWsCleanupHandler implements LogoutHandler {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public LogoutWsCleanupHandler(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
                       Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        if (username != null) {
            chatWebSocketHandler.removeUserSession(username);
        }
    }
}