package com.arshad.chat_backend.repository;


import com.arshad.chat_backend.entity.ChatRoom;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends MongoRepository<ChatRoom, String> {
    Optional<ChatRoom> findByName(String name);
    void deleteByName(String roomId);
    List<ChatRoom> findByMembersContaining(String username);
}