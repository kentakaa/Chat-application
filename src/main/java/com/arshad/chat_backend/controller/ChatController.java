package com.arshad.chat_backend.controller;

import com.arshad.chat_backend.entity.ChatMessage;

import com.arshad.chat_backend.repository.ChatRepository;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.arshad.chat_backend.repository.RoomRepository;
import java.util.List;
import org.springframework.web.bind.annotation.ResponseBody;
import java.security.Principal;
import org.springframework.ui.Model;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ChatController {

    @Autowired
    private ChatRepository chatRepository;

    // Yeh 2 lines add karni hain
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/")
    public String accountDashboard(Model model, Principal principal) {
        // If user logged in then move it to the model
        if (principal != null) {
            model.addAttribute("username", principal.getName());
        }
        return "account"; // account.html
    }

    @GetMapping("/chat")
    public String chatPage(Model model, Principal principal) {
        if (principal != null) {
            // Backend pick the name from authentic session and pass to frontend
            model.addAttribute("username", principal.getName());
        } else {
            return "redirect:/login";
        }
        return "chat";// chat.html
    }

 @MessageMapping("/chat/{roomId}/sendMessage")
    public void sendMessage(@DestinationVariable String roomId, @Payload ChatMessage chatMessage) {

        log.info("RECEIVED: WebSocket message for room '{}' from sender '{}'", roomId, chatMessage.getSender());

        // indep typing router without saving in db
        if ("TYPING".equalsIgnoreCase(chatMessage.getType())) {
            log.info("user ko indicator show ho rha hai",roomId);
            messagingTemplate.convertAndSend("/topic/" + roomId, chatMessage);
            return; // Yahan se turant wapas laut jao!
        }

        try {
            com.arshad.chat_backend.entity.ChatRoom room = roomRepository.findByName(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found in DB: " + roomId));

            String currentStatus = room.getRequestStatus();

            // 🛑 1. NAYI SECURITY (The Zombie Fix)
            if ("REJECTED".equalsIgnoreCase(currentStatus) || "CLOSED".equalsIgnoreCase(currentStatus)) {
                log.warn("SECURITY BLOCK: Message dropped. Room '{}' is currently {}.", roomId, currentStatus);
                return; 
            }

            // 🛡️ 2. SPAM GUARD (Pending State Logic)
            if ("PENDING".equalsIgnoreCase(currentStatus)) {
                if (chatMessage.getSender().equalsIgnoreCase(room.getInitiator())) {
                    boolean alreadySentFirstMessage = chatRepository.existsByRoomIdAndSender(roomId, chatMessage.getSender());
                    if (alreadySentFirstMessage) {
                        log.warn("SPAM GUARD: Initiator '{}' tried to send a 2nd message in PENDING state.", chatMessage.getSender());
                        return; 
                    }
                } else {
                    log.warn("BLOCKED: Receiver cannot send a message without ACCEPTING first.");
                    return; 
                }
            }

            
            chatMessage.setRoomId(roomId);
            chatRepository.save(chatMessage);
            
            // 📝 Wapas MongoDB kar diya bhai!
            log.info("SAVED: Message safely stored in MongoDB for room '{}'", roomId);

            messagingTemplate.convertAndSend("/topic/" + roomId, chatMessage);
            log.info("BROADCASTED: Message sent to topic '/topic/{}'", roomId);

        } catch (Exception e) {
            log.error("CRITICAL STOMP ERROR: Failed to process message for room '{}'", roomId, e);
        }
    }

    @MessageMapping("/chat/{roomId}/addUser")
    public void addUser(@DestinationVariable String roomId, @Payload ChatMessage chatMessage,
            SimpMessageHeaderAccessor headerAccessor) {

        java.util.Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            sessionAttributes.put("username", chatMessage.getSender());
        } else {

            log.error("Warning: WebSocket session attributes are null for user " + chatMessage.getSender());
        }
    }

    @GetMapping("/api/history")
    @ResponseBody
    public List<ChatMessage> getChatHistory(@RequestParam(defaultValue = "general") String room) {

        return chatRepository.findByRoomId(room);
    }
    
}