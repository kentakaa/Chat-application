package com.arshad.chat_backend.service;

import com.arshad.chat_backend.dto.RoomLeaveEvent;
import com.arshad.chat_backend.entity.ChatRoom;
import com.arshad.chat_backend.entity.User;
import com.arshad.chat_backend.entity.UserStatus;
import com.arshad.chat_backend.repository.ChatRepository;
import com.arshad.chat_backend.repository.RoomRepository;
import com.arshad.chat_backend.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
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
    @Mock
    private UserRepository userRepository; // Step 1 firewall ke liye (unstubbed → empty Optional → firewall pass)

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
        // firewall: sirf non-creator ("zoe") lookup hota hai — creator skip (continue)
        User zoe = new User();
        zoe.setUsername("zoe");
        zoe.setStatus(UserStatus.OPEN_TO_CHAT);
        when(userRepository.findByUsername("zoe")).thenReturn(Optional.of(zoe));
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

    // ---------- 6. GHOST USER: unknown target → 404-worthy NSE ----------
    @Test
    void createRoom_unknownTarget_throwsNoSuchElementException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ChatRoom request = new ChatRoom();
        request.setMembers(new ArrayList<>(List.of("alice", "ghost")));

        assertThatThrownBy(() -> roomService.createRoomAndBroadcast(request, "alice"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("not found");
        verify(roomRepository, never()).save(any()); // room bana hi nahi
    }

    // ---------- helpers (Step 8) ----------
    private ChatRoom dmRoom(String name, String a, String b, String status, String initiator) {
        ChatRoom room = new ChatRoom();
        room.setName(name);
        room.setMembers(new ArrayList<>(List.of(a, b)));
        room.setRequestStatus(status);
        room.setInitiator(initiator);
        room.setGroupChat(false);
        return room;
    }

    private User user(String name, UserStatus status) {
        User u = new User();
        u.setUsername(name);
        u.setStatus(status);
        return u;
    }

    // ---------- 7. SOCIAL: friends filter ----------
    @Test
    @DisplayName("Friends: only ACCEPTED live DMs (groups/deleted/other-status out)")
    void friendDms_mixedRooms_returnsOnlyAcceptedLiveDMs() {
        ChatRoom friend = dmRoom("DM-alice-AND-bob", "alice", "bob", "ACCEPTED", "alice");
        ChatRoom pending = dmRoom("DM-alice-AND-carol", "alice", "carol", "PENDING", "carol");
        ChatRoom rejected = dmRoom("DM-alice-AND-dave", "alice", "dave", "REJECTED", "dave");
        ChatRoom closed = dmRoom("DM-alice-AND-erin", "alice", "erin", "CLOSED", "alice");
        ChatRoom group = dmRoom("GROUP-xyz", "alice", "bob", "ACCEPTED", "alice");
        group.setGroupChat(true);
        ChatRoom deleted = dmRoom("DM-alice-AND-frank", "alice", "frank", "ACCEPTED", "alice");
        deleted.setDeletedBy(new ArrayList<>(List.of("alice")));
        when(roomRepository.findByMembersContaining("alice"))
                .thenReturn(List.of(friend, pending, rejected, closed, group, deleted));

        List<ChatRoom> result = roomService.getFriendDms("alice");

        assertThat(result).containsExactly(friend);
    }

    // ---------- 8. SOCIAL: empty ----------
    @Test
    void friendDms_noRooms_returnsEmpty() {
        when(roomRepository.findByMembersContaining("alice")).thenReturn(List.of());

        assertThat(roomService.getFriendDms("alice")).isEmpty();
    }

    // ---------- 9. SOCIAL: incoming direction ----------
    @Test
    void pendingRequests_onlyIncomingPending() {
        ChatRoom incoming = dmRoom("DM-alice-AND-bob", "alice", "bob", "PENDING", "bob");
        ChatRoom outgoing = dmRoom("DM-alice-AND-carol", "alice", "carol", "PENDING", "alice");
        ChatRoom accepted = dmRoom("DM-alice-AND-dave", "alice", "dave", "ACCEPTED", "dave");
        when(roomRepository.findByMembersContaining("alice"))
                .thenReturn(List.of(incoming, outgoing, accepted));

        List<ChatRoom> result = roomService.getPendingRequests("alice");

        assertThat(result).containsExactly(incoming);
    }

    // ---------- 10. SOCIAL: outgoing direction ----------
    @Test
    void pendingSent_onlyOutgoingPending() {
        ChatRoom incoming = dmRoom("DM-alice-AND-bob", "alice", "bob", "PENDING", "bob");
        ChatRoom outgoing = dmRoom("DM-alice-AND-carol", "alice", "carol", "PENDING", "alice");
        when(roomRepository.findByMembersContaining("alice"))
                .thenReturn(List.of(incoming, outgoing));

        List<ChatRoom> result = roomService.getPendingSent("alice");

        assertThat(result).containsExactly(outgoing);
    }

    // ---------- 11. FIREWALL: sleeping target ----------
    @Test
    @DisplayName("Firewall: SLEEPING target blocks DM creation")
    void dm_sleepingTarget_throwsSecurityException() {
        when(userRepository.findByUsername("bob"))
                .thenReturn(Optional.of(user("bob", UserStatus.SLEEPING)));

        ChatRoom request = new ChatRoom();
        request.setMembers(new ArrayList<>(List.of("alice", "bob")));

        assertThatThrownBy(() -> roomService.createRoomAndBroadcast(request, "alice"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("sleeping");
        verify(roomRepository, never()).save(any());
    }

    // ---------- 12. FIREWALL: existing room bypasses ----------
    @Test
    @DisplayName("Firewall: existing room returns without user lookup (old friendship safe)")
    void dm_existingRoom_bypassesFirewall() {
        ChatRoom existing = dmRoom("DM-alice-AND-bob", "alice", "bob", "ACCEPTED", "alice");
        when(roomRepository.findByName("DM-alice-AND-bob")).thenReturn(Optional.of(existing));

        ChatRoom request = new ChatRoom();
        request.setMembers(new ArrayList<>(List.of("alice", "bob")));

        ChatRoom result = roomService.createRoomAndBroadcast(request, "alice");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).findByUsername(any()); // firewall tak pahuncha hi nahi
        verify(roomRepository, never()).save(any());
    }

    // ---------- 13. STATUS: non-member ----------
    @Test
    void status_nonMember_throwsSecurityException() {
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dmRoom("DM-alice-AND-bob", "alice", "bob", "PENDING", "alice")));

        assertThatThrownBy(() -> roomService.updateRoomStatus("DM-alice-AND-bob", "ACCEPTED", "mallory"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a member");
        verify(roomRepository, never()).save(any());
    }

    // ---------- 14. STATUS: non-pending ----------
    @Test
    void status_nonPendingRoom_throwsIllegalArgumentException() {
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dmRoom("DM-alice-AND-bob", "alice", "bob", "ACCEPTED", "alice")));

        assertThatThrownBy(() -> roomService.updateRoomStatus("DM-alice-AND-bob", "REJECTED", "bob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer pending");
        verify(roomRepository, never()).save(any());
    }

    // ---------- 15. STATUS: initiator self ----------
    @Test
    void status_initiatorSelf_throwsSecurityException() {
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dmRoom("DM-alice-AND-bob", "alice", "bob", "PENDING", "alice")));

        assertThatThrownBy(() -> roomService.updateRoomStatus("DM-alice-AND-bob", "ACCEPTED", "alice"))
                .isInstanceOf(SecurityException.class);
        verify(roomRepository, never()).save(any());
    }

    // ---------- 16. STATUS: invalid value ----------
    @Test
    void status_invalidValue_throwsIllegalArgumentException() {
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dmRoom("DM-alice-AND-bob", "alice", "bob", "PENDING", "alice")));

        assertThatThrownBy(() -> roomService.updateRoomStatus("DM-alice-AND-bob", "CLOSED", "bob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid status");
        verify(roomRepository, never()).save(any());
    }

    // ---------- 17. STATUS: unknown room ----------
    @Test
    void status_unknownRoom_throwsNoSuchElementException() {
        when(roomRepository.findByName("DM-nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.updateRoomStatus("DM-nope", "ACCEPTED", "bob"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Room not found");
    }

    // ---------- 18. STATUS: happy path + broadcast ----------
    @Test
    void status_receiverAccepts_marksAcceptedAndBroadcasts() {
        when(roomRepository.findByName("DM-alice-AND-bob"))
                .thenReturn(Optional.of(dmRoom("DM-alice-AND-bob", "alice", "bob", "PENDING", "alice")));
        when(roomRepository.save(any(ChatRoom.class))).thenAnswer(i -> i.getArgument(0));

        ChatRoom updated = roomService.updateRoomStatus("DM-alice-AND-bob", "ACCEPTED", "bob");

        assertThat(updated.getRequestStatus()).isEqualTo("ACCEPTED");
        verify(eventPublisher).publishEvent(any(RoomLeaveEvent.class)); // WS broadcast hua
    }
}
