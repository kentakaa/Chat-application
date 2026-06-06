package com.arshad.chat_backend.entity;

import org.springframework.data.annotation.Id; // <-- MongoDB wala Id import
import org.springframework.data.mongodb.core.index.Indexed; // <-- Unique constraint ke liye
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Document(collection = "users") // @Entity aur @Table ki jagah
@Data
public class User {

    @Id
    private String id; // <-- ID hamesha String rahegi MongoDB mein

    // MongoDB level par unique constraint lagane ke liye @Indexed(unique = true)
    @Indexed(unique = true) 
    private String username;

    // @Column(nullable = false) hata diya. Validation DTOs ke through handle karenge.
    private String password;

    // College phase ke baad industry level UI ke liye placeholder
    private String profilePicUrl; 
    private AccountType accountType;

    // Default presence firewall state
    private UserStatus status = UserStatus.OPEN_TO_CHAT;
  
}