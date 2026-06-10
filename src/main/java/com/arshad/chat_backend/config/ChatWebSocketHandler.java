package com.arshad.chat_backend.config;

import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;
import com.arshad.chat_backend.dto.RoomLeaveEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final ChatRepository chatRepository;
    private final RoomRepository roomRepository;

    // 🚀 PRINCIPAL METADATA CACHE: MongoDB network hits ko bachaane ke liye internal state store
    private final Map<String, String> roomStatusCache = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatRepository chatRepository, RoomRepository roomRepository) {
        this.chatRepository = chatRepository;
        this.roomRepository = roomRepository;
    }

    // Dynamic cache update hook (Jab HTTP controller status change kare tab bypass)
    public void evictRoomCache(String roomId) {
        if (roomId != null) {
            this.roomStatusCache.remove(roomId);
        }
    }

    @EventListener
    public void handleRoomLeaveEvent(RoomLeaveEvent event) {
        try {
            log.info("BACKGROUND THREAD: Broadcasting leave event for room {}", event.getRoomName());
            // Safe eviction to refresh status on next lookup
            roomStatusCache.remove(event.getRoomName());
            broadcastToRoom(event.getRoomName(), event.getPayload());
        } catch (IOException e) {
            log.error("Failed to broadcast background event", e);
        }
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("Naya connection open hua! Total users: {}", sessions.size());
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);
            String roomId = chatMessage.getRoomId();
            String messageType = chatMessage.getType();
            String sender = chatMessage.getSender();

            //  SECURITY & CLEAN LOG OPTIMIZATION: Raw data hidden, metadata parsed securely
            if ("TYPING".equalsIgnoreCase(messageType)) {
                log.debug("User '{}' is typing in room '{}'", sender, roomId); // DEBUG level prevents production flooding
                broadcastToRoom(roomId, payload);
                return; // Short-circuit logic: Immediate drop-out without database validation overhead
            } else {
                log.info("INCOMING PACKET: Type=[{}] | Sender='{}' | Room='{}' | Size={} bytes", 
                         messageType, sender, roomId, payload.length());
            }

            //STATE ENGINE LOOKUP: Local map lookups are $O(1)$ constant time operations
            String currentStatus = roomStatusCache.get(roomId);
            if (currentStatus == null) {
                ChatRoom room = roomRepository.findByName(roomId)
                        .orElseThrow(() -> new RuntimeException("Room not found in DB: " + roomId));
                currentStatus = room.getRequestStatus();
                roomStatusCache.put(roomId, currentStatus); // Warm up local system storage cache
            }

            // Zombie System Execution Interception Guard
            if ("REJECTED".equalsIgnoreCase(currentStatus) || "CLOSED".equalsIgnoreCase(currentStatus)) {
                log.warn("SECURITY BLOCK: Message dropped for closed/rejected room '{}'", roomId);
                return;
            }

            // Spam Control Layer for standard PENDING request frames
            if ("PENDING".equalsIgnoreCase(currentStatus)) {
                ChatRoom room = roomRepository.findByName(roomId).orElse(null);
                if (room != null) {
                    if (sender.equalsIgnoreCase(room.getInitiator())) {
                        boolean alreadySentFirstMessage = chatRepository.existsByRoomIdAndSender(roomId, sender);
                        if (alreadySentFirstMessage) {
                            log.warn("SPAM GUARD: Dropped secondary execution message trace from initiator.");
                            return;
                        }
                    } else {
                        log.warn("SECURITY BREACH: Unauthorized receiver message dispatch attempt on PENDING state.");
                        return;
                    }
                }
            }

            // Core persistence transaction pipeline execution
            chatRepository.save(chatMessage);
            broadcastToRoom(roomId, payload);

        } catch (Exception e) {
            log.error("CRITICAL WEB SOCKET ERROR inside processing terminal layer", e);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("Connection closed. Remaining online users: {}", sessions.size());
    }

    private void broadcastToRoom(String roomId, @NonNull String payload) throws IOException {
        TextMessage responseMessage = new TextMessage(payload);
        for (WebSocketSession activeSession : sessions) {
            if (activeSession.isOpen()) {
                activeSession.sendMessage(responseMessage);
            }
        }
    }
}