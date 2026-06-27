package com.arshad.chat_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher; 
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.ArrayList;
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
    private ApplicationEventPublisher eventPublisher; 

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==========================================
    // 1. GET ALL ROOMS (Fixed Stream Filter)
    // ==========================================
    @GetMapping
    public ResponseEntity<?> getUserRooms(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized access.");
        String loggedInUser = principal.getName();
        try {
            List<ChatRoom> allMyRooms = roomRepository.findByMembersContaining(loggedInUser);
            List<ChatRoom> visibleRooms = allMyRooms.stream()
                .filter(room -> {
                    // 🎯 SENIOR FIX: Agar user ka naam deletedBy list me hai, toh usko chat mat dikhao
                    if (room.getDeletedBy() != null && room.getDeletedBy().contains(loggedInUser)) {
                        return false; 
                    }
                    return true;
                })
                .collect(Collectors.toList());
            return ResponseEntity.ok(visibleRooms);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to fetch rooms for user: {}", loggedInUser, e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    // ==========================================
    // 2. CREATE ROOM
    // ==========================================
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody ChatRoom room, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized access.");
        try {
            ChatRoom savedRoom = roomService.createRoomAndBroadcast(room, principal.getName());
            return ResponseEntity.ok(savedRoom);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to create room", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    // ==========================================
    // 3. UPDATE STATUS
    // ==========================================
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

    
    // ==========================================
    // 4. LEAVE / DELETE ROOM (Strictly for DMs )
    // ==========================================
    @PutMapping("/{roomName}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomName, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        String currentUser = principal.getName();

        ChatRoom room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new RuntimeException("Room not found!"));

        // 🛑 STRICT BOUNDARY: Group chat logic blocked for future implementation
        if (room.isGroupChat()) {
            // Tum future me iska logic alag API endpoint (e.g., /api/groups/{id}/leave) me likh sakte ho
            return ResponseEntity.status(501).body("Group chat leave logic will be implemented later.");
        }

        // 👇 YAHAN SE NEECHE SIRF DM (1-on-1) KA LOGIC HAI 👇

        if ("REJECTED".equalsIgnoreCase(room.getRequestStatus()) || "CLOSED".equalsIgnoreCase(room.getRequestStatus())) {
            
            if (room.getDeletedBy() == null) {
                room.setDeletedBy(new ArrayList<>());
            }
            if (!room.getDeletedBy().contains(currentUser)) {
                room.getDeletedBy().add(currentUser);
            }

            // Kyunki ye DM hai, array me humesha max 2 hi log honge
            boolean bothDeleted = room.getMembers().stream()
                                     .allMatch(member -> room.getDeletedBy().contains(member));

            if (bothDeleted) {
                chatRepository.deleteByRoomId(roomName); 
                roomRepository.deleteByName(roomName);
                log.info("MUTUAL CONSENT MET: DM '{}' permanently wiped.", roomName);
            } else {
                roomRepository.save(room);
            }
            return ResponseEntity.ok().body("DM deleted from your view.");
        }

        room.setRequestStatus("CLOSED");
        if (room.getClosedAt() == null) {
            room.setClosedAt(java.time.LocalDateTime.now());
        }
        roomRepository.save(room);

        ChatMessage leaveSignal = new ChatMessage();
        leaveSignal.setRoomId(roomName);
        leaveSignal.setSender("SYSTEM");
        leaveSignal.setContent(currentUser + " has left the chat.");
        leaveSignal.setType("LEAVE_EVENT"); 

        try {
            String jsonPayload = objectMapper.writeValueAsString(leaveSignal);
            eventPublisher.publishEvent(new RoomLeaveEvent(roomName, jsonPayload));
        } catch (Exception e) {
            log.error("Failed to publish leave event for room '{}'", roomName, e);
        }

        return ResponseEntity.ok().body("Chat left successfully");
    }
}