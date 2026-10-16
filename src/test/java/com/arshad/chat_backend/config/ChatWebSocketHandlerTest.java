package com.arshad.chat_backend.config;

import com.arshad.chat_backend.entity.ChatMessage;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatWebSocketHandlerTest {

    @Mock private ChatRepository chatRepository;
    @Mock private RoomRepository roomRepository;

    @InjectMocks private ChatWebSocketHandler chatWebSocketHandler;

    // NOTE: getAttributes stub helper me NAHI — sirf establish-path use karta hai
    // (message-path principal se resolve hota hai; unused stub = strict-stubs fail).
    // getUri stub bilkul nahi — naya code URI padhta hi nahi.
    private WebSocketSession session(String principalName) {
        WebSocketSession s = mock(WebSocketSession.class);
        if (principalName != null) {
            Principal p = mock(Principal.class);
            when(p.getName()).thenReturn(principalName);
            when(s.getPrincipal()).thenReturn(p);
        }
        return s;
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
    void establish_principalMapped_receivesOwnMessages() throws Exception {
        WebSocketSession s = session("alice");
        when(s.getAttributes()).thenReturn(new ConcurrentHashMap<String, Object>());
        when(s.isOpen()).thenReturn(true);
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dm("DM-alice-AND-bob", "ACCEPTED")));
        chatWebSocketHandler.afterConnectionEstablished(s);

        chatWebSocketHandler.broadcastToRoom("DM-alice-AND-bob", "{\"x\":1}");

        verify(s).sendMessage(any(TextMessage.class));
    }

    @Test
    void establish_spoofQueryIgnored_attackerGetsNothing() throws Exception {
        // purana code ?user=alice padhke mallory ko alice map karta tha;
        // naya code URI padhta hi nahi — sirf principal (mallory) map hota hai
        WebSocketSession mallory = session("mallory");
        WebSocketSession alice = session("alice");
        when(mallory.getAttributes()).thenReturn(new ConcurrentHashMap<String, Object>());
        when(alice.getAttributes()).thenReturn(new ConcurrentHashMap<String, Object>());
        when(alice.isOpen()).thenReturn(true);
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dm("DM-alice-AND-bob", "ACCEPTED")));
        chatWebSocketHandler.afterConnectionEstablished(mallory);
        chatWebSocketHandler.afterConnectionEstablished(alice);

        chatWebSocketHandler.broadcastToRoom("DM-alice-AND-bob", "{\"x\":1}");

        verify(alice).sendMessage(any(TextMessage.class));
        verify(mallory, never()).sendMessage(any(TextMessage.class)); // ← S2 pin
    }

    @Test
    void establish_noPrincipal_closesSession() throws Exception {
        WebSocketSession s = session(null);

        chatWebSocketHandler.afterConnectionEstablished(s);

        verify(s).close(any(CloseStatus.class));
    }

    @Test
    void message_senderMismatch_droppedBeforeDb() throws Exception {
        WebSocketSession s = session("alice");
        String payload = "{\"sender\":\"mallory\",\"content\":\"hi\",\"type\":\"CHAT\",\"roomId\":\"DM-alice-AND-bob\"}";

        chatWebSocketHandler.handleTextMessage(s, new TextMessage(payload));

        verify(roomRepository, never()).findByName(any());
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void message_serverTypeFromClient_dropped() throws Exception {
        WebSocketSession s = session("alice");
        String payload = "{\"sender\":\"alice\",\"content\":\"x\",\"type\":\"SYSTEM\",\"roomId\":\"DM-alice-AND-bob\"}";

        chatWebSocketHandler.handleTextMessage(s, new TextMessage(payload));

        verify(chatRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void message_legitChat_savedAndBroadcast() throws Exception {
        WebSocketSession s = session("alice");
        when(s.getAttributes()).thenReturn(new ConcurrentHashMap<String, Object>());
        when(s.isOpen()).thenReturn(true);
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dm("DM-alice-AND-bob", "ACCEPTED")));
        chatWebSocketHandler.afterConnectionEstablished(s);

        String payload = "{\"sender\":\"alice\",\"content\":\"hi\",\"type\":\"CHAT\",\"roomId\":\"DM-alice-AND-bob\"}";
        chatWebSocketHandler.handleTextMessage(s, new TextMessage(payload));

        verify(chatRepository).save(any(ChatMessage.class));
        verify(s).sendMessage(any(TextMessage.class));
    }
}
