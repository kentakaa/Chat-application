package com.arshad.chat_backend.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "chat_rooms")
@Data
@NoArgsConstructor 
public class ChatRoom {

    @Id
    private String id; // MongoDB ka auto-generated Object ID

    // ==========================================
    // CORE IDENTIFIERS
    // ==========================================
    private String name; // 🎯 UNIQUE ROUTING ID (e.g., "DM-thor-AND-ijaj" ya "GROUP-8f7d9...")
    private String displayName; // 🎯 NEW: UI me dikhane ke liye (e.g., Group ka naam "College Friends")
    
    private boolean isGroupChat;
    private String requestStatus; // PENDING, ACCEPTED, REJECTED

    // ==========================================
    // ROLES & MEMBERS
    // ==========================================
    private String initiator; // Sirf DM ke liye (Kisne request bheji)
    private String admin;     // Sirf Group ke liye (Group admin kaun hai)
    
    private List<String> members = new ArrayList<>(); // Strict array of usernames

    // ==========================================
    // STATE & TRACKING
    // ==========================================
    
    private List<String> deletedBy = new ArrayList<>(); 

    // 🚀 FUTURE FEATURE: Instagram Vanish Mode toggle
    private boolean isVanishMode = false;

    // ==========================================
    // TIMESTAMPS
    // ==========================================
    private LocalDateTime createdAt = LocalDateTime.now();
    
    
    private LocalDateTime updatedAt = LocalDateTime.now(); 
    
    private LocalDateTime closedAt; 
}