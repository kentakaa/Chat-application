package com.arshad.chat_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher; // NAYA: Decoupled events ke liye
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;
import com.arshad.chat_backend.service.RoomService;
import com.arshad.chat_backend.dto.RoomLeaveEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private ApplicationEventPublisher eventPublisher; // Purely decoupled

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<?> getUserRooms(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized access.");
        String loggedInUser = principal.getName();
        try {
            List<ChatRoom> allMyRooms = roomRepository.findByMembersContaining(loggedInUser);
            List<ChatRoom> visibleRooms = allMyRooms.stream()
                .filter(room -> {
                    boolean isInitiator = loggedInUser.equalsIgnoreCase(room.getInitiator());
                    if (isInitiator && room.isDeletedByInitiator()) return false;
                    if (!isInitiator && room.isDeletedByReceiver()) return false;
                    return true;
                })
                .collect(Collectors.toList());
            return ResponseEntity.ok(visibleRooms);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to fetch rooms for user: {}", loggedInUser, e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody ChatRoom room, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized access.");
        try {
            ChatRoom savedRoom = roomService.createGroupAndBroadcast(room, principal.getName());
            return ResponseEntity.ok(savedRoom);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to create room", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @PutMapping("/{roomName}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String roomName, 
            @RequestBody com.arshad.chat_backend.dto.StatusUpdateRequest request, 
            Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        try {
            var updatedRoom = roomService.updateRoomStatus(roomName, request.getRequestStatus(), principal.getName());
            return ResponseEntity.ok(updatedRoom);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{roomName}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomName, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        String currentUser = principal.getName();

        ChatRoom room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new RuntimeException("Room not found!"));

        boolean isInitiator = room.getInitiator() != null && room.getInitiator().equalsIgnoreCase(currentUser);

        if ("REJECTED".equalsIgnoreCase(room.getRequestStatus()) || "CLOSED".equalsIgnoreCase(room.getRequestStatus())) {
            if (isInitiator) {
                room.setDeletedByInitiator(true);
            } else {
                room.setDeletedByReceiver(true);
            }

            if (room.isDeletedByInitiator() && room.isDeletedByReceiver()) {
                chatRepository.deleteByRoomId(roomName); 
                roomRepository.deleteByName(roomName);
                log.info("MUTUAL CONSENT MET: Room '{}' permanently wiped.", roomName);
            } else {
                roomRepository.save(room);
            }
            return ResponseEntity.ok().body("Room deleted from your view.");
        }

        room.setRequestStatus("CLOSED");
        room.setLeftBy(currentUser);
        if (room.getClosedAt() == null) {
            room.setClosedAt(java.time.LocalDateTime.now());
        }
        roomRepository.save(room);

        // System Signal Package
        ChatMessage leaveSignal = new ChatMessage();
        leaveSignal.setRoomId(roomName);
        leaveSignal.setSender("SYSTEM");
        leaveSignal.setContent(currentUser + " has left the room.");
        leaveSignal.setType("LEAVE_EVENT"); 

        try {
            String jsonPayload = objectMapper.writeValueAsString(leaveSignal);
            
            // 🔥 PRINCIPLE APPROACH: Fire and Forget Event. HTTP thread is now free!
            eventPublisher.publishEvent(new RoomLeaveEvent(roomName, jsonPayload));
            log.info("EVENT PUBLISHED: Decoupled leave event for room '{}'", roomName);
        } catch (Exception e) {
            log.error("Failed to publish leave event for room '{}'", roomName, e);
        }

        return ResponseEntity.ok().body("Room left successfully");
    }
}