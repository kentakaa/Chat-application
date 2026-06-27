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

@Component
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

  
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> firstMessageSent = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatRepository chatRepository;
    private final RoomRepository roomRepository;

    private final Map<String, String> roomStatusCache = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatRepository chatRepository, RoomRepository roomRepository) {
        this.chatRepository = chatRepository;
        this.roomRepository = roomRepository;
      
    }

    // Dynamic cache update hook
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
    // 1. Pehle purane tareeke se dhoondhne ki koshish karo
    String userId = (String) session.getAttributes().get("userId");

    // 2. Agar null mile, toh naye Native URL tareeke se uthao
    if (userId == null && session.getUri() != null && session.getUri().getQuery() != null) {
        String query = session.getUri().getQuery();
        if (query.contains("user=")) {
            userId = query.split("user=")[1].split("&")[0];
        }       
    }

    // 3. Map me properly save karo
    if (userId != null && !userId.isEmpty() && !"null".equals(userId)) {
        userSessions.put(userId, session);
        log.info("New connection established user: {} | Total users: {}", userId, userSessions.size());
    } else {
        log.error(" CRITICAL: Connection established but userId is NULL!");
    }
}

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);
            String roomId = chatMessage.getRoomId();
            String messageType = chatMessage.getType();
            String sender = chatMessage.getSender();

            if ("TYPING".equalsIgnoreCase(messageType)) {
                log.debug("User '{}' is typing in room '{}'", sender, roomId);

                broadcastToRoom(roomId, payload);
                return; // Immediate drop-out without database validation overhead
            } else {
                log.info("INCOMING PACKET: Type=[{}] | Sender='{}' | Room='{}' | Size={} bytes",
                        messageType, sender, roomId, payload.length());
            }

            String currentStatus = roomStatusCache.get(roomId);
            if (currentStatus == null) {
                ChatRoom room = roomRepository.findByName(roomId)
                        .orElseThrow(() -> new RuntimeException("Room not found in DB: " + roomId));
                currentStatus = room.getRequestStatus();
                roomStatusCache.put(roomId, currentStatus);
            }

            if ("REJECTED".equalsIgnoreCase(currentStatus) || "CLOSED".equalsIgnoreCase(currentStatus)) {
                log.warn("SECURITY BLOCK: Message dropped for closed/rejected room '{}'", roomId);
                return;
            }

            if ("PENDING".equalsIgnoreCase(currentStatus)) {
                // fetching details of room if room not exist give pass null for security
                ChatRoom room = roomRepository.findByName(roomId).orElse(null);
                // if get null then condition is not meet then return if details get then go
                // ahead
                if (room != null) {
                    // weather check is it chat initiator aur reciever if initiator
                    if (sender.equalsIgnoreCase(room.getInitiator())) {
                        // check weather initiator already sent request as firstmessage it will give 0/1

                        Boolean hasSent = firstMessageSent.get(roomId);

                        if (hasSent != null && hasSent) {
                            log.warn("Spam gaurd: Request has pushed to reciever");
                            return;
                        }
                        boolean alreadySentFirstMessage = chatRepository.existsByRoomIdAndSender(roomId, sender);
                        // if request sent then return
                        if (alreadySentFirstMessage) {
                            firstMessageSent.put(roomId, true);
                            log.warn("SPAM GUARD:arn Dropped secondary execution message trace from initiator.");
                            return;
                        }
                    } else {
                        log.warn("SECURITY BREACH: Unauthorized receiver message dispatch attempt on PENDING state.");
                        return;
                    }
                }
            }
            chatRepository.save(chatMessage);
            broadcastToRoom(roomId, payload);
            
            if ("PENDING".equalsIgnoreCase(currentStatus)) {
                firstMessageSent.put(roomId, true);
            }
        } catch (Exception e) {
            log.error("CRITICAL WEB SOCKET ERROR inside processing terminal layer", e);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(userId, session);
        }
        log.info("Connection closed. Remaining online users {}", userSessions.size());
    }

   private void broadcastToRoom(String roomId, @NonNull String payload) throws IOException {
    // 1. Database se room uthao (ya cache se agar tumne cache use kiya hai)
    ChatRoom room = roomRepository.findByName(roomId)
            .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

    TextMessage responseMessage = new TextMessage(payload);
    
    // 2. Room object se seedha members ki list nikalo
    List<String> members = room.getMembers();

    // 3. Sabhi members ko broadcast karo
    for (String member : members) {
        WebSocketSession targetSession = userSessions.get(member);
        if (targetSession != null && targetSession.isOpen()) {
            targetSession.sendMessage(responseMessage);
            log.debug("Message routed successfully for user {}", member);
        }
    }
}
}