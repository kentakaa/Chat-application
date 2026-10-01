package com.arshad.chat_backend.controller;

import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatControllerTest {

    @Mock private ChatRepository chatRepository;
    @Mock private RoomRepository roomRepository;

    @InjectMocks private ChatController chatController;

    private Principal user(String name) {
        Principal p = mock(Principal.class);
        when(p.getName()).thenReturn(name);
        return p;
    }

    private ChatRoom dm(String name, String status, String initiator) {
        ChatRoom room = new ChatRoom();
        room.setName(name);
        room.setMembers(new ArrayList<>(List.of("alice", "bob")));
        room.setRequestStatus(status);
        room.setInitiator(initiator);
        room.setGroupChat(false);
        return room;
    }

    @Test
    void history_nonMember_forbiddenWithoutTouchingMessages() {
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dm("DM-alice-AND-bob", "ACCEPTED", "alice")));

        ResponseEntity<?> result = chatController.getChatHistory("DM-alice-AND-bob", user("mallory"));

        assertThat(result.getStatusCode().value()).isEqualTo(403);
        verify(chatRepository, never()).findByRoomId(any()); // messages DB touch hi nahi hua
    }

    @Test
    void history_unknownRoom_notFound() {
        when(roomRepository.findByName("DM-nope")).thenReturn(Optional.empty());

        ResponseEntity<?> result = chatController.getChatHistory("DM-nope", user("alice"));

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        verify(chatRepository, never()).findByRoomId(any());
    }

    @Test
    void history_member_returnsMessages() {
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dm("DM-alice-AND-bob", "ACCEPTED", "alice")));
        ChatMessage msg = new ChatMessage();
        msg.setRoomId("DM-alice-AND-bob");
        msg.setSender("alice");
        msg.setContent("hi");
        when(chatRepository.findByRoomId("DM-alice-AND-bob")).thenReturn(List.of(msg));

        ResponseEntity<?> result = chatController.getChatHistory("DM-alice-AND-bob", user("bob"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(List.of(msg));
    }

    @Test
    void history_deletedByMe_forbidden() {
        ChatRoom room = dm("DM-alice-AND-bob", "ACCEPTED", "alice");
        room.setDeletedBy(new ArrayList<>(List.of("alice"))); // maine apni view se hataya
        when(roomRepository.findByName("DM-alice-AND-bob")).thenReturn(Optional.of(room));

        ResponseEntity<?> result = chatController.getChatHistory("DM-alice-AND-bob", user("alice"));

        assertThat(result.getStatusCode().value()).isEqualTo(403);
        verify(chatRepository, never()).findByRoomId(any());
    }

    @Test
    void history_unauthenticated_unauthorized() {
        ResponseEntity<?> result = chatController.getChatHistory("DM-alice-AND-bob", null);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        verify(roomRepository, never()).findByName(any());
        verify(chatRepository, never()).findByRoomId(any());
    }
}
