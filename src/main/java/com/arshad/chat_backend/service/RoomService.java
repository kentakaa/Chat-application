package com.arshad.chat_backend.service;

import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;
import com.arshad.chat_backend.dto.RoomLeaveEvent; // Generic event carrier for system messages
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher; // Decoupled broadcasting
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher; // NAYA: STOMP template ki jagah injection

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 1. CREATE ROOM / DM LOGIC
    public ChatRoom createGroupAndBroadcast(ChatRoom room, String creator) {
        String safeName = room.getName().toLowerCase().replaceAll("\\s+", "-");
        var existingRoom = roomRepository.findByName(safeName);

        if (existingRoom.isPresent()) {
            log.info("Room '{}' already exists. Returning existing room.", safeName);
            return existingRoom.get();
        }

        room.setName(safeName);

        if (room.getMembers() == null) {
            room.setMembers(new ArrayList<>());
        }

        if (safeName.contains("_")) {
            // DM (Direct Message) State Setup
            String[] users = safeName.split("_");
            room.getMembers().add(users[0]);
            room.getMembers().add(users[1]);
            room.setRequestStatus("PENDING"); 
            room.setInitiator(creator);
        } else {
            // Group Chat State Setup
            room.getMembers().add(creator);
            room.setAdmin(creator); 
            room.setRequestStatus("ACCEPTED");
        }

        ChatRoom savedRoom = roomRepository.save(room);

        // System Broadcast Signal Packet
        ChatMessage systemMsg = new ChatMessage();
        systemMsg.setRoomId(safeName);
        systemMsg.setSender("SYSTEM");
        systemMsg.setContent("Room initialized by " + creator);
        systemMsg.setType("SYSTEM");

        try {
            // Database save taaki chat history mein persist kare
            chatRepository.save(systemMsg);
            
            // Decoupled Background Event Core Pipe line mein push kiya
            String jsonPayload = objectMapper.writeValueAsString(systemMsg);
            eventPublisher.publishEvent(new RoomLeaveEvent(safeName, jsonPayload));
        } catch (Exception e) {
            log.error("Failed to broadcast system initialization message for room: {}", safeName, e);
        }

        return savedRoom;
    }

    // 2. ADMIN AUTHORITY CHECK (Future Use)
    public boolean addMemberToGroup(String roomId, String newMember, String requester) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new IllegalArgumentException("Room ID cannot be null or empty!");
        }
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getAdmin().equals(requester)) {
            log.warn("Security Alert: User {} unauthorized add attempt in {}", requester, roomId);
            return false;
        }

        room.getMembers().add(newMember);
        roomRepository.save(room);
        return true;
    }

    // 3. UPDATE ROOM STATUS (Accept/Reject DM Requests)
    public ChatRoom updateRoomStatus(String roomName, String newRequestStatus, String currentUser) {
        ChatRoom room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new RuntimeException("Room not found!"));

        if (currentUser.equalsIgnoreCase(room.getInitiator())) {
            throw new RuntimeException("Initiator cannot accept/reject their own request!");
        }

        room.setRequestStatus(newRequestStatus);
        ChatRoom updatedRoom = roomRepository.save(room);

        String broadcastText = "";
        if ("ACCEPTED".equalsIgnoreCase(newRequestStatus)) {
            String currentDate = new java.text.SimpleDateFormat("dd MMM yyyy").format(new java.util.Date());
            broadcastText = currentUser + " accepted the request on " + currentDate;
        } else if ("REJECTED".equalsIgnoreCase(newRequestStatus)) {
            broadcastText = "Your request is rejected";
        }

        if (!broadcastText.isEmpty()) {
            ChatMessage systemMsg = new ChatMessage();
            systemMsg.setRoomId(roomName);
            systemMsg.setSender("SYSTEM");
            systemMsg.setContent(broadcastText);
            systemMsg.setType("SYSTEM");

            try {
                chatRepository.save(systemMsg); 
                
                // REST API Context execution thread free up via events
                String jsonPayload = objectMapper.writeValueAsString(systemMsg);
                eventPublisher.publishEvent(new RoomLeaveEvent(roomName, jsonPayload));
            } catch (Exception e) {
                log.error("Failed to broadcast updated status system msg for room: {}", roomName, e);
            }
        }

        return updatedRoom;
    }
}