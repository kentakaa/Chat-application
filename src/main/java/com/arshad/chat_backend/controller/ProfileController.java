package com.arshad.chat_backend.controller;
import com.arshad.chat_backend.service.ContactService;
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
 @Autowired 
 private ContactService contactService;

    @GetMapping("/profile")
    public String showProfilePage(Model model, Principal principal) {

        
        if (principal == null) {
            return "redirect:/login";
        }

        
        String loggedInUsername = principal.getName();
        model.addAttribute("username", loggedInUsername);

        
        Optional<User> userOptional = userRepository.findByUsername(loggedInUsername);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
           
            model.addAttribute("accountType", user.getAccountType());
            model.addAttribute("status", user.getStatus());
            Map<String,Long> counts = contactService.getCounts(loggedInUsername);
            model.addAttribute("friendsCount", counts.getOrDefault("contacts", 0L));
            model.addAttribute("requestsCount", counts.getOrDefault("requests", 0L));
            
        }

        return "account";
    }



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
