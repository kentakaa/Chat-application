package com.arshad.chat_backend.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "contacts")
@Data @NoArgsConstructor
public class Contact {
    @Id private String id;
    private List<String> members;      // sorted pair [a,b] — DM jaisa deterministic identity
    private String requester;          // kisne request bheji
    private String status;             // "PENDING" | "ACCEPTED"
    private LocalDateTime createdAt = LocalDateTime.now();
}