package com.arshad.chat_backend.controller;

import com.arshad.chat_backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    
    @Autowired
    private UserService userService;

    // API Endpoint: GET /api/users/search?q=rahul
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestParam("q") String query, Principal principal) {
        
        // 1. The Bouncer (Strict Security)
        if (principal == null) {
            log.warn("WARNING: Unauthorized search attempt.");
            return ResponseEntity.status(401).body("Unauthorized access. Please login.");
        }

        // 2. Resource Saving (Bad Request)
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Search query cannot be empty");
        }

        try {
            String currentUser = principal.getName();
            log.info("User '{}' is searching for: '{}'", currentUser, query);

            var results = userService.searchUsers(query.trim(), currentUser);
            
            // 4. Proper 200 OK ke sath results bhej diye
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            // 5. The Airbag (Try-Catch)
            log.error("CRITICAL: Search failed for query: {}", query, e);
            return ResponseEntity.status(500).body("Internal server error during search.");
        }
    }
}