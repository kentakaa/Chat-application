package com.arshad.chat_backend.task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j; 
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;
@Slf4j
@Component
public class RoomCleanupScheduler {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ChatRepository chatRepository;

    // Yeh cron job har 1 ghante (3,600,000 milliseconds) mein automatically chalega
    @Scheduled(fixedRate = 3600000) 
    public void cleanupExpiredRooms() {
        log.info(" RUNNING CRON JOB: Checking for expired chat rooms...");

        // Aaj ke time se exactly 24 ghante piche ka time nikal lo
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        // Saare rooms database se nikalo
        List<ChatRoom> allRooms = roomRepository.findAll();

        for (ChatRoom room : allRooms) {
            // Check karo ki kya room closed hai aur usko close hue 24h se zyada ho gaye hain?
            if (room.getClosedAt() != null && room.getClosedAt().isBefore(twentyFourHoursAgo)) {
                
                String roomName = room.getName();
                
                // 1. Saare cascading messages delete karo
                chatRepository.deleteByRoomId(roomName);
                
                // 2. Room ko permanently delete karo
                roomRepository.delete(room);
                
                log.info("🗑️ DEAD ROOM WIPED: Room '{}' exceeded 24h limit and was hard deleted.", roomName);
            }
        }
    }
}
