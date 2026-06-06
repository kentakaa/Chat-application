package com.arshad.chat_backend.entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Document(collection = "chat_messages")
@Data
@NoArgsConstructor
public class ChatMessage {

@Id
    private String id;


    private String sender;
    private String content;
    private String type;
    
    private LocalDateTime timestamp = LocalDateTime.now();


    @Field("room_id") 
    private String roomId;
   
}