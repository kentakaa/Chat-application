package com.arshad.chat_backend.service;

import com.arshad.chat_backend.entity.User;
import com.arshad.chat_backend.repository.UserRepository;
import com.arshad.chat_backend.dto.UserSearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public List<UserSearchResponse> searchUsers(String query, String currentUsername) {

        List<User> users = userRepository.findByUsernameContainingIgnoreCase(query);

      
        return users.stream()
                // Khud ka naam search result mein nahi aana chahiye
                .filter(user -> !user.getUsername().equalsIgnoreCase(currentUsername))
                // User entity ko DTO mein convert karo taaki password leak na ho
                .map(user -> new UserSearchResponse( 
                        user.getUsername(), 
                        user.getProfilePicUrl()
                ))
                .collect(Collectors.toList());
    }
}