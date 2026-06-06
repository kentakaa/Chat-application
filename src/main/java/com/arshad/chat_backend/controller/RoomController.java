package com.arshad.chat_backend.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.*;
import java.security.Principal;

import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;
import java.util.List;
import java.util.stream.Collectors;

import com.arshad.chat_backend.service.RoomService;
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
    private SimpMessagingTemplate messagingTemplate;

  
    @Autowired
private RoomService roomService;

    // 1. FETCH USER ROOMS (Read Operation)
    @GetMapping
    public ResponseEntity<?> getUserRooms(Principal principal) {
        if (principal == null) {
            log.warn("WARNING: Unauthorized attempt to fetch rooms.");
            return ResponseEntity.status(401).body("Unauthorized access.");
        }
        
        String loggedInUser = principal.getName();
        
        try {
            // 1. Database se user ke saare rooms uthao (Active + Hidden)
            List<ChatRoom> allMyRooms = roomRepository.findByMembersContaining(loggedInUser);
            
            // 🛑 2. NAYA FILTER: Wo rooms hata do jo is user ne delete (hide) kar diye hain
            List<ChatRoom> visibleRooms = allMyRooms.stream()
                .filter(room -> {
                    boolean isInitiator = loggedInUser.equalsIgnoreCase(room.getInitiator());
                    
                    // Agar main initiator hu aur maine delete daba diya hai, toh list mein mat dikhao
                    if (isInitiator && room.isDeletedByInitiator()) return false;
                    
                    // Agar main receiver hu aur maine delete daba diya hai, toh list mein mat dikhao
                    if (!isInitiator && room.isDeletedByReceiver()) return false;
                    
                    return true; // Baaki saare normal rooms dikhao
                })
                .collect(Collectors.toList());

            // 3. BREADCRUMB: Log ko bhi thoda smart bana diya taaki debug karne mein asani ho
            log.info("Fetched {} visible rooms for user: {} (out of {} total connected rooms)", 
                     visibleRooms.size(), loggedInUser, allMyRooms.size());
            
            return ResponseEntity.ok(visibleRooms);
            
        } catch (Exception e) {
            // AIRBAG FOR DB CRASH
            log.error("CRITICAL: Failed to fetch rooms for user: {}", loggedInUser, e);
            return ResponseEntity.status(500).body("Internal Server Error while fetching rooms.");
        }
    }

    // 2. CREATE A NEW GROUP ROOM (Write Operation)
 @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody ChatRoom room, Principal principal) {
        
        if (principal == null) {
            log.warn("WARNING: Unauthorized attempt to create a room.");
            return ResponseEntity.status(401).body("Unauthorized access.");
        }

        try {
           
            ChatRoom savedRoom = roomService.createGroupAndBroadcast(room, principal.getName());
            
            return ResponseEntity.ok(savedRoom);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to create room and broadcast for user: {}", principal.getName(), e);
            return ResponseEntity.status(500).body("Internal Server Error while creating the room.");
        }
    }
    @PutMapping("/{roomName}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String roomName, 
            @RequestBody com.arshad.chat_backend.dto.StatusUpdateRequest request, 
            Principal principal) {
        
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");

        try {
            // ✅ request.getRequestStatus() call kiya
            var updatedRoom = roomService.updateRoomStatus(roomName, request.getRequestStatus(), principal.getName());
            return ResponseEntity.ok(updatedRoom);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PutMapping("/{roomName}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomName, Principal principal) {
        String currentUser = principal.getName();

        ChatRoom room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new RuntimeException("Room not found!"));

boolean isInitiator = room.getInitiator() != null && room.getInitiator().equalsIgnoreCase(currentUser);

        // ==========================================
        // 🚨 SCENARIO 1: USER CLICKS "DELETE ROOM"
        // (Chat pehle se hi REJECTED ya CLOSED hai)
        // ==========================================
        if ("REJECTED".equalsIgnoreCase(room.getRequestStatus()) || "CLOSED".equalsIgnoreCase(room.getRequestStatus())) {
            
            // 1. Jisne click kiya hai, uska delete flag TRUE kar do
            if (isInitiator) {
                room.setDeletedByInitiator(true);
            } else {
                room.setDeletedByReceiver(true);
            }

            // 2. 🧠 MUTUAL CONSENT CHECK   
            if (room.isDeletedByInitiator() && room.isDeletedByReceiver()) {
                // Agar dono ne delete daba diya hai, tabhi HARD DELETE karo!
                chatRepository.deleteByRoomId(roomName); 
                roomRepository.deleteByName(roomName);
                log.info("MUTUAL CONSENT MET: Room '{}' permanently wiped out.", roomName);
            } else {
                // Agar samne wale ne abhi delete nahi kiya hai, toh DB mein save rakho
                roomRepository.save(room);
                log.info("HALF DELETED: User '{}' hid room '{}', waiting for other user or 24h timer.", currentUser, roomName);
            }

            // Frontend JS already API ke response ka wait kar rahi hai hide karne ke liye
            // Isliye yahan se faaltu WebSocket message hata diya gaya hai.
            return ResponseEntity.ok().body("Room deleted from your view.");
        }

      
        room.setRequestStatus("CLOSED");
        room.setLeftBy(currentUser);
        
        
        if (room.getClosedAt() == null) {
            room.setClosedAt(java.time.LocalDateTime.now()); // Database mein timestamp save
        }
        
        roomRepository.save(room);

        // Samne wale ko Lock karne ke liye broadcast karo
        ChatMessage leaveSignal = new ChatMessage();
        leaveSignal.setRoomId(roomName);
        leaveSignal.setSender("SYSTEM");
        leaveSignal.setContent(currentUser + " has left the room.");
        leaveSignal.setType("LEAVE_EVENT"); 
        messagingTemplate.convertAndSend("/topic/" + roomName, leaveSignal);

        return ResponseEntity.ok().body("Room left successfully");
    }
    
}