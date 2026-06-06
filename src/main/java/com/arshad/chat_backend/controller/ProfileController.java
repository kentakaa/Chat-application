package com.arshad.chat_backend.controller;

import com.arshad.chat_backend.entity.User;
import com.arshad.chat_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.security.Principal;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import com.arshad.chat_backend.dto.StatusUpdateRequest;
import java.util.Map;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ProfileController {

    @Autowired
    private UserRepository userRepository;


    @GetMapping("/profile")
    public String showProfilePage(Model model, Principal principal) {

        
        if (principal == null) {
            return "redirect:/login";
        }

        // 2. Logged-in user ka username nikalo
        String loggedInUsername = principal.getName();
        model.addAttribute("username", loggedInUsername);

        
        Optional<User> userOptional = userRepository.findByUsername(loggedInUsername);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            // HTML par variables bhej rahe hain
            model.addAttribute("accountType", user.getAccountType());
            model.addAttribute("status", user.getStatus());
            // Dummy logic for friends count (abhi ke liye)
            model.addAttribute("friendsCount", 12);
            model.addAttribute("requestsCount", 3);
        }

        // Yeh "account.html" file ko render karega
        return "account";
    }


    // NAYA API ENDPOINT: Frontend se JSON receive karne ke liye
    @PostMapping("/api/profile/status")
    @ResponseBody 
    public ResponseEntity<?> updateNetworkStatus(@RequestBody StatusUpdateRequest request, Principal principal) {
        
        Map<String, String> response = new HashMap<>();

  
        if (principal == null) {

        log.warn("WARNING: unauthorrised access attempt");
            response.put("error", "Unauthorized access. Please login.");
            return ResponseEntity.status(401).body(response);
        }

        String username = principal.getName();
        Optional<User> userOptional = userRepository.findByUsername(username);

        // Check 2: Database mein user milna chahiye
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            try {
                
                user.setStatus(request.getStatus());
                userRepository.save(user);
                
                log.info("Network Status Succesfully updated to {} for the {}",request.getStatus(),username);
                response.put("message", "Network firewall updated successfully to "+request.getStatus());
                return ResponseEntity.ok(response);
            }
         catch (Exception e) {
           log.error("CRITICAL: Database save failed for user {}", username, e);
                response.put("error", "Internal server error while updating profile.");
                return ResponseEntity.status(500).body(response);
                
        }
        
        } else {
           log.warn("Status aborted database can't find profile for {}",username);
            response.put("error", "User profile corrupted or not found.");
            return ResponseEntity.status(404).body(response);
        }
    }    

    
}
