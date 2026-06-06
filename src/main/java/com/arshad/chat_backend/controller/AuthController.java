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

    // IF someone hit this url it will be redirect to registeration page
    @GetMapping("/register")
    public String showRegistrationForm() {
        return "register"; 
    }
    // if someone hit it then it will redirect to login page
@GetMapping("/login")
public String loginPage() {
    return "login";     
}
    // Registeration form filled by user then check the validation
   @PostMapping("/register")
    public String processRegistration(@RequestParam String username,
                                      @RequestParam String password,
                                      @RequestParam AccountType accountType,
                                      RedirectAttributes redirectAttributes) {

        // Check 1: Kya username pehle se occupied hai?
        if (userRepository.findByUsername(username).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Username is already taken. Try another one!");
            return "redirect:/register"; // Error ke sath wapas form par bhej do
        }

        // Creating new user
        User newUser = new User();
        newUser.setUsername(username);
        // Password encryption
        newUser.setPassword(passwordEncoder.encode(password)); 
        newUser.setAccountType(accountType); 
        newUser.setStatus(UserStatus.OPEN_TO_CHAT);

        userRepository.save(newUser);

        // Once account created then redirect to login page
        return "redirect:/login?success";
}
}