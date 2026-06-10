package com.arshad.chat_backend.repository;

import com.arshad.chat_backend.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatRepository extends MongoRepository<ChatMessage, String> {
    
    List<ChatMessage> findByRoomId(String roomId);
    void deleteByRoomId(String roomId);
boolean existsByRoomIdAndSender(String roomId, String sender);
}       