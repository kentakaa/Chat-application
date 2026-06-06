package com.arshad.chat_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login","/register", "/css/**", "/js/**").permitAll() // Inko bina login ke allow karo
                        .anyRequest().authenticated() // Baaki sabke liye login zaroori hai
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/chat", true) // <-- Yeh line login hote hi chat par phekegi
                        .permitAll())

                .logout(logout -> logout.permitAll())
                .csrf(csrf -> csrf.disable()); // Development ke liye CSRF disable kar rahe hain

        return http.build();
    }

    // Yeh tool tumhare password ko hash (encrypt) karega
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
