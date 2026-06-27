package com.arshad.chat_backend.controller;
import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.repository.ChatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.ui.Model;
import java.security.Principal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Controller
public class ChatController {

    @Autowired
    private ChatRepository chatRepository;

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
        return "chat"; // chat.html (Vanilla Thymeleaf)
    }
// not production ready 
    // 3. Chat History Endpoint: Puraane messages MongoDB se fetch karne ke liye
    @GetMapping("/api/history")
    @ResponseBody
    public List<ChatMessage> getChatHistory(@RequestParam(defaultValue = "general") String room) {
        log.info("HTTP REQUEST: Fetching chat history from MongoDB for room '{}'", room);
        return chatRepository.findByRoomId(room);
    }
}