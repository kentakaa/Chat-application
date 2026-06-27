package com.arshad.chat_backend.service;

import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;
import com.arshad.chat_backend.dto.RoomLeaveEvent; 
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher; 
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher; 

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==========================================
    // 1. MAIN ROUTER (Controller isko call karega)
    // ==========================================
    public ChatRoom createRoomAndBroadcast(ChatRoom room, String creator) {
        if (room == null || creator == null) {
            throw new IllegalArgumentException("Room payload or creator cannot be null");
        }

        // Traffic Police: Decide karo ki DM hai ya Group Chat
        if (room.isGroupChat()) {
            return createGroupChatRoom(room, creator);
        } else {
            return createDirectMessageRoom(room, creator);
        }
    }

    // ==========================================
    // 1A. DIRECT MESSAGE (DM) LOGIC
    // ==========================================
    private ChatRoom createDirectMessageRoom(ChatRoom room, String creator) {
        if (room.getMembers() == null) room.setMembers(new ArrayList<>());

        List<String> members = room.getMembers();
        Collections.sort(members); 
        
        String uniqueRoomName = "DM-" + members.get(0) + "-AND-" + members.get(1);
        
        var existingRoom = roomRepository.findByName(uniqueRoomName);
        if (existingRoom.isPresent()) {
            log.info("DM Room '{}' already exists.", uniqueRoomName);
            return existingRoom.get();
        }

        room.setName(uniqueRoomName);
        room.setRequestStatus("PENDING"); 
        room.setInitiator(creator);
        room.setGroupChat(false); 
        
        ChatRoom savedRoom = roomRepository.save(room);

        // Naye helper method ka use kiya
        broadcastSystemMessage(savedRoom.getName(), "Room initialized by " + creator);

        return savedRoom;
    }

    // ==========================================
    // 1B. GROUP CHAT LOGIC 
    // ==========================================
    private ChatRoom createGroupChatRoom(ChatRoom room, String creator) {
        if (room.getMembers() == null) room.setMembers(new ArrayList<>());

        String groupUuid = "GROUP-" + java.util.UUID.randomUUID().toString();
        room.setName(groupUuid); 
        
        if (!room.getMembers().contains(creator)) {
            room.getMembers().add(creator);
        }
        
        room.setAdmin(creator); 
        room.setRequestStatus("ACCEPTED");
        room.setGroupChat(true); 
        
        ChatRoom savedRoom = roomRepository.save(room);

        // Naye helper method ka use kiya
        broadcastSystemMessage(savedRoom.getName(), "Group initialized by " + creator);

        return savedRoom;
    }

    // ==========================================
    // 2. ADMIN AUTHORITY CHECK (Tumhara purana code - As it is)
    // ==========================================
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

    // ==========================================
    // 3. UPDATE ROOM STATUS (Accept/Reject DM Requests)
    // ==========================================
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

        // 🎯 SENIOR DEV MOVE: Purana try-catch hata kar helper method reuse kiya
        if (!broadcastText.isEmpty()) {
            broadcastSystemMessage(roomName, broadcastText);
        }

        return updatedRoom;
    }

    // ==========================================
    // 4. COMMON HELPER: BROADCAST SYSTEM MESSAGE
    // ==========================================
    private void broadcastSystemMessage(String roomName, String messageContent) {
        ChatMessage systemMsg = new ChatMessage();
        systemMsg.setRoomId(roomName);
        systemMsg.setSender("SYSTEM");
        systemMsg.setContent(messageContent); // Ab ye dynamic text lega
        systemMsg.setType("SYSTEM");

        try {
            chatRepository.save(systemMsg);
            String jsonPayload = objectMapper.writeValueAsString(systemMsg);
            eventPublisher.publishEvent(new RoomLeaveEvent(roomName, jsonPayload));
        } catch (Exception e) {
            log.error("Failed to broadcast system message for room: {}", roomName, e);
        }
    }
}