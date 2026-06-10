    package com.arshad.chat_backend.config;

    import org.springframework.context.annotation.Configuration;
    import org.springframework.lang.NonNull;
    import org.springframework.web.socket.config.annotation.EnableWebSocket;
    import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
    import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
    import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

    @Configuration
    @EnableWebSocket 
    public class WebSocketConfig implements WebSocketConfigurer {

        private final ChatWebSocketHandler chatWebSocketHandler;

        // Constructor Injection ke zariye custom handler ko configure kiya
        public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
            this.chatWebSocketHandler = chatWebSocketHandler;
        }

        @Override
        public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
            registry.addHandler(chatWebSocketHandler, "/ws") // Connection endpoint: ws://localhost:8080/ws
                    .setAllowedOriginPatterns("*") // cors
                    .addInterceptors(new HttpSessionHandshakeInterceptor()); 
        }
    }