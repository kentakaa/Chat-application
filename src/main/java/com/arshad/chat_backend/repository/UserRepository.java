package com.arshad.chat_backend.repository;

import com.arshad.chat_backend.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface UserRepository extends MongoRepository<User, String> {
    
    Optional<User> findByUsername(String username); 
    List<User> findByUsernameContainingIgnoreCase(String username);
}