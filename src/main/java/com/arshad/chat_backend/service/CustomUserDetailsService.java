package com.arshad.chat_backend.service;
import lombok.extern.slf4j.Slf4j;
import com.arshad.chat_backend.entity.User;
import com.arshad.chat_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
 public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        // 1. Database se user dhoondo. Agar nahi mila toh directly error throw kar do.
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        // 2. Agar mil gaya, toh Spring Security ke standard 'User' object me convert karke return kar do
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword()) // Yeh DB wala encrypted password hona chahiye
                .roles("USER") // Default role set kar diya
                .build();
    }
}