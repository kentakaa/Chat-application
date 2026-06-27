package com.arshad.chat_backend.controller;

import com.arshad.chat_backend.entity.AccountType;
import com.arshad.chat_backend.entity.User;
import com.arshad.chat_backend.entity.UserStatus;
import com.arshad.chat_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @GetMapping("/register")
    public String showRegistrationForm() {
        return "register"; 
    }
    
@GetMapping("/login")
public String loginPage() {
    return "login";     
}

   @PostMapping("/register")
    public String processRegistration(@RequestParam String username,
                                      @RequestParam String password,
                                      @RequestParam AccountType accountType,
                                      RedirectAttributes redirectAttributes) {

        
        if (userRepository.findByUsername(username).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Username is already taken. Try another one!");
            return "redirect:/register"; 
        }

        // Creating new user
        User newUser = new User();
        newUser.setUsername(username);
        // Password encryption
        newUser.setPassword(passwordEncoder.encode(password)); 
        newUser.setAccountType(accountType); 
        newUser.setStatus(UserStatus.OPEN_TO_CHAT);

        userRepository.save(newUser);

        
        return "redirect:/login?success";
}
}