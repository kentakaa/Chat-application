package com.arshad.chat_backend.controller;

import com.arshad.chat_backend.config.ChatWebSocketHandler;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;
import com.arshad.chat_backend.service.RoomService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RoomControllerTest {

    @Mock private ChatWebSocketHandler chatWebSocketHandler;
    @Mock private ChatRepository chatRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomService roomService;

    @InjectMocks private RoomController roomController;

    private Principal user(String name) {
        Principal p = mock(Principal.class);
        when(p.getName()).thenReturn(name);
        return p;
    }

    private ChatRoom dm(String name, String status) {
        ChatRoom room = new ChatRoom();
        room.setName(name);
        room.setMembers(new ArrayList<>(List.of("alice", "bob")));
        room.setRequestStatus(status);
        room.setInitiator("alice");
        room.setGroupChat(false);
        return room;
    }

    @Test
    void leaveRoom_mutualWipe_evictsCacheAndDeletes() {
        ChatRoom room = dm("DM-alice-AND-bob", "REJECTED");
        room.setDeletedBy(new ArrayList<>(List.of("bob")));
        when(roomRepository.findByName("DM-alice-AND-bob")).thenReturn(Optional.of(room));

        var result = roomController.leaveRoom("DM-alice-AND-bob", user("alice"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(chatRepository).deleteByRoomId("DM-alice-AND-bob");
        verify(roomRepository).deleteByName("DM-alice-AND-bob");
        verify(chatWebSocketHandler).evictRoomCache("DM-alice-AND-bob");
    }

    @Test
    void leaveRoom_liveRoom_closesEvictsAndBroadcasts() throws Exception {
        ChatRoom room = dm("DM-alice-AND-bob", "ACCEPTED");
        when(roomRepository.findByName("DM-alice-AND-bob")).thenReturn(Optional.of(room));
        when(roomRepository.save(any(ChatRoom.class))).thenAnswer(i -> i.getArgument(0));

        var result = roomController.leaveRoom("DM-alice-AND-bob", user("alice"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(room.getRequestStatus()).isEqualTo("CLOSED");
        verify(chatWebSocketHandler).evictRoomCache("DM-alice-AND-bob");
        verify(chatWebSocketHandler).broadcastToRoom(eq("DM-alice-AND-bob"), anyString());
    }
}
