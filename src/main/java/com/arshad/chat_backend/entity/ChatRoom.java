package com.arshad.chat_backend.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "chat_rooms") //
@Data //
@NoArgsConstructor 
public class ChatRoom {

    @Id
    private String id;
    private boolean isGroupChat;
    private String name;
    private String requestStatus;
    private String leftBy;
    private String initiator;
    private LocalDateTime createdAt = LocalDateTime.now();
    private List<String> members = new ArrayList<>();
    private String admin;
    // Naye Tracking Variables
    private LocalDateTime closedAt;         
    private boolean deletedByInitiator = false; 
    private boolean deletedByReceiver = false;  
}   