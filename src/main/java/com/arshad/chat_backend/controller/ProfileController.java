package com.arshad.chat_backend.controller;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.service.RoomService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private RoomService roomService;




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
            
            
        }

List<ChatRoom> friendDms = roomService.getFriendDms(loggedInUsername);
        List<ChatRoom> incomingRooms = roomService.getPendingRequests(loggedInUsername);
        List<ChatRoom> outgoingRooms = roomService.getPendingSent(loggedInUsername);

        model.addAttribute("friendsCount", friendDms.size());
        model.addAttribute("requestsCount", incomingRooms.size());

        List<String> friendList = new ArrayList<>();
        for (ChatRoom r : friendDms) {
            for (String m : r.getMembers()) {
                if (!m.equals(loggedInUsername)) { friendList.add(m); break; }
            }
        }
        model.addAttribute("friendList", friendList);

        List<Map<String, String>> incomingRequests = new ArrayList<>();
        for (ChatRoom r : incomingRooms) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("roomName", r.getName());
            item.put("username", r.getInitiator());
            incomingRequests.add(item);
        }
        model.addAttribute("incomingRequests", incomingRequests);

        List<Map<String, String>> outgoingRequests = new ArrayList<>();
        for (ChatRoom r : outgoingRooms) {
            String other = "?";
            for (String m : r.getMembers()) {
                if (!m.equals(loggedInUsername)) { other = m; break; }
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("roomName", r.getName());
            item.put("username", other);
            outgoingRequests.add(item);
        }
        model.addAttribute("outgoingRequests", outgoingRequests);
















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
