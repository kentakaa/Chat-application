package com.arshad.chat_backend.service;

import com.arshad.chat_backend.dto.RoomLeaveEvent;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RoomServiceTest {
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RoomService roomService;

    @Test
    @DisplayName("Group create: GROUP-uuid + displayName preserve + admin=creator")
    void createRoom_groupChat_generatesUuidAndKeepsDisplayName() {
        // Arrange
        ChatRoom request = new ChatRoom();
        request.setGroupChat(true);
        request.setDisplayName("College Friends");
        request.setMembers(new ArrayList<>());
        when(roomRepository.save(any(ChatRoom.class)))
                .thenAnswer(inv -> inv.getArgument(0)); // save wahi object wapas de (jaise Mongo)

        // Act
        ChatRoom saved = roomService.createRoomAndBroadcast(request, "arshad");

        // Assert
        assertThat(saved.getName()).startsWith("GROUP-");
        assertThat(saved.getDisplayName()).isEqualTo("College Friends"); // bug fix ka proof
        assertThat(saved.getAdmin()).isEqualTo("arshad");
        assertThat(saved.getRequestStatus()).isEqualTo("ACCEPTED");
        assertThat(saved.isGroupChat()).isTrue();
        assertThat(saved.getMembers()).contains("arshad");
        verify(chatRepository).save(any()); // system message save hua
        verify(eventPublisher).publishEvent(any(RoomLeaveEvent.class));
    }

    // ---------- 2. NULL GUARD ----------
    @Test
    void createRoom_nullPayload_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> roomService.createRoomAndBroadcast(null, "arshad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    // ---------- 3. DM: sorted name + PENDING ----------
    @Test
    void createRoom_twoMembers_createsPendingDmWithSortedName() {
        // Arrange (members deliberately unsorted: zoe pehle)
        ChatRoom request = new ChatRoom();
        request.setMembers(new ArrayList<>(List.of("zoe", "arshad")));
        when(roomRepository.findByName("DM-arshad-AND-zoe")).thenReturn(Optional.empty());
        when(roomRepository.save(any(ChatRoom.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ChatRoom saved = roomService.createRoomAndBroadcast(request, "arshad");

        // Assert
        assertThat(saved.getName()).isEqualTo("DM-arshad-AND-zoe"); // sort verify
        assertThat(saved.getRequestStatus()).isEqualTo("PENDING");
        assertThat(saved.getInitiator()).isEqualTo("arshad");
    }

    // ---------- 4. DUPLICATE DM: save mat bulao ----------
    @Test
    void createRoom_existingDm_returnsExistingWithoutSaving() {
        ChatRoom request = new ChatRoom();
        request.setMembers(new ArrayList<>(List.of("zoe", "arshad")));
        ChatRoom existing = new ChatRoom();
        existing.setName("DM-arshad-AND-zoe");
        when(roomRepository.findByName("DM-arshad-AND-zoe")).thenReturn(Optional.of(existing));

        ChatRoom result = roomService.createRoomAndBroadcast(request, "arshad");

        assertThat(result).isSameAs(existing); // same object, naya nahi bana
        verify(roomRepository, never()).save(any()); // "DO NOT" assertion
    }

    // ---------- 5. EMPTY MEMBERS: Fix A ka guard ----------
    @Test
    @DisplayName("Empty members DM must be rejected with 400-worthy IAE (Fix A)")
    void createRoom_dmWithEmptyMembers_throwsIllegalArgumentException() {
        ChatRoom request = new ChatRoom(); // members = []

        assertThatThrownBy(() -> roomService.createRoomAndBroadcast(request, "arshad"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
