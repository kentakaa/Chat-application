package com.arshad.chat_backend.service;

import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service // Yeh annotation Spring ko batata hai ki yeh humara Business Logic hai
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;
@Autowired
    private ChatRepository chatRepository;
    @Autowired
    private SimpMessagingTemplate messagingTemplate; // Broadcast karne ke liye Walkie-Talkie

    // 1. GROUP CREATE KARNE KA LOGIC
    public ChatRoom createGroupAndBroadcast(ChatRoom room, String creator) {

        // Logic 1: Safe Name generate karo
        String safeName = room.getName().toLowerCase().replaceAll("\\s+", "-");

        var existingRoom = roomRepository.findByName(safeName);

        if (existingRoom.isPresent()) {
            log.info("Room '{}' already exists. Returning existing room.", safeName);
            // Agar room pehle se hai, toh naya mat banao, wahi purana lauta do!
            return existingRoom.get();
        }

        room.setName(safeName);

        // Logic 2: Member initialize karo
        if (room.getMembers() == null) {
            room.setMembers(new ArrayList<>());
        }
if (safeName.contains("_")) {
            // Agar "jhon_kerb" hai, toh split karke dono ko add karo
            String[] users = safeName.split("_");
            room.getMembers().add(users[0]); // Adds arshad/jhon
            room.getMembers().add(users[1]);
            room.setRequestStatus("PENDING"); 
            room.setInitiator(creator); // Adds kerb
        } else {
            // Agar group hai, toh sirf creator ko daalo (baad mein invite karega)
            room.getMembers().add(creator);
            room.setAdmin(creator); 
            room.setRequestStatus("ACCEPTED");// 🎉 Creator ab is group ka Admin hai!
        }
        // Logic 3: Admin Authority Set Karo
   

        // Logic 4: Database mein save karo
        ChatRoom savedRoom = roomRepository.save(room);

        // Logic 5: THE BROADCAST MESSAGE
        String systemMessage = "This group was created by " + creator;
        log.info("System Broadcast: {}", systemMessage);

        // WebSocket par sabko bata do ki naya group ban gaya hai
        // (Yahan tum future mein ChatMessage entity save karwaoge)
        messagingTemplate.convertAndSend("/topic/" + savedRoom.getId(), systemMessage);

        return savedRoom;
    }

    // 2. ADMIN AUTHORITY CHECK KARNE KA LOGIC (Future use)
    public boolean addMemberToGroup(String roomId, String newMember, String requester) {

        if (roomId == null || roomId.trim().isEmpty()) {
            throw new IllegalArgumentException("Room ID cannot be null or empty!");
        }
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // SOLID Principle: Sirf Admin hi add kar sakta hai!
        if (!room.getAdmin().equals(requester)) {
            log.warn("Security Alert: User {} tried to add member to room {} without admin rights!", requester, roomId);
            return false; // Permission Denied
        }

        room.getMembers().add(newMember);
        roomRepository.save(room);
        return true;
    }
  public ChatRoom updateRoomStatus(String roomName, String newRequestStatus, String currentUser) {
        
        ChatRoom room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new RuntimeException("Room not found!"));

        if (currentUser.equalsIgnoreCase(room.getInitiator())) {
            throw new RuntimeException("Initiator cannot accept/reject their own request!");
        }

        // ✅ Status update kiya aur room save kiya
        room.setRequestStatus(newRequestStatus);
        ChatRoom updatedRoom = roomRepository.save(room);

        String broadcastText = "";
            
        if ("ACCEPTED".equalsIgnoreCase(newRequestStatus)) {
            String currentDate = new java.text.SimpleDateFormat("dd MMM yyyy").format(new java.util.Date());
            broadcastText = currentUser + " accepted the request on " + currentDate;
        } else if ("REJECTED".equalsIgnoreCase(newRequestStatus)) {
            broadcastText = "Your request is rejected";
        }

        // 🛑 NAYA LOGIC: System message banakar save aur broadcast karna
        if (!broadcastText.isEmpty()) {
            ChatMessage systemMsg = new ChatMessage();
            systemMsg.setRoomId(roomName); // Frontend currentRoom se match karega
            systemMsg.setSender("SYSTEM");
            systemMsg.setContent(broadcastText);
            systemMsg.setType("SYSTEM");

            // Database mein save kiya taaki History load hone par dikhe
            chatRepository.save(systemMsg); 
            
            // Live WebSocket par bhej diya
            messagingTemplate.convertAndSend("/topic/" + roomName, systemMsg);
        }

        return updatedRoom;
    }
}