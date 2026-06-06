package com.arshad.chat_backend.dto;

import com.arshad.chat_backend.entity.UserStatus;
import lombok.Data;

@Data
public class StatusUpdateRequest {
    
    private UserStatus status; 
    private String requestStatus;
    
}
    