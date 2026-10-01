package com.arshad.chat_backend.controller;
import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.ui.Model;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Controller
public class ChatController {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private RoomRepository roomRepository;

    // root after succesfull login
    @GetMapping("/")
    public String accountDashboard(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("username", principal.getName());
        }
        return "account"; // account.html (Vanilla Thymeleaf RN)
    }

    
    @GetMapping("/chat")
    public String chatPage(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("username", principal.getName());
        } else {
            return "redirect:/login";
        }
        return "chat"; // 
    }

    @GetMapping("/api/history")
    @ResponseBody
    public ResponseEntity<?> getChatHistory(@RequestParam(defaultValue = "general") String room,
                                            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        String user = principal.getName();

        ChatRoom chatRoom = roomRepository.findByName(room).orElse(null);
        if (chatRoom == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Room not found"));
        }
        
        if (!chatRoom.getMembers().contains(user)
                || (chatRoom.getDeletedBy() != null && chatRoom.getDeletedBy().contains(user))) {
            log.warn("History blocked for non-member {} on room {}", user, room);
            return ResponseEntity.status(403).body(Map.of("message", "You are not a member of this chat"));
        }

        log.info("HTTP REQUEST: Fetching chat history from MongoDB for room '{}'", room);
        return ResponseEntity.ok(chatRepository.findByRoomId(room));
    }
}