package com.arshad.chat_backend.service;

import com.arshad.chat_backend.entity.Contact;
import com.arshad.chat_backend.entity.User;
import com.arshad.chat_backend.entity.UserStatus;
import com.arshad.chat_backend.repository.ContactRepository;
import com.arshad.chat_backend.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ContactService contactService;

    private User user(String name, UserStatus status) {
        User u = new User();
        u.setUsername(name);
        u.setStatus(status);
        return u;
    }

    private Contact contact(String id, String a, String b, String requester, String status) {
        List<String> members = new ArrayList<>(List.of(a, b));
        members.sort(String::compareTo);
        Contact c = new Contact();
        c.setId(id);
        c.setMembers(members);
        c.setRequester(requester);
        c.setStatus(status);
        return c;
    }
    
    @Test
    @DisplayName("Send request: PENDING contact saved with sorted pair")
    void sendRequest_validTarget_savesPendingContact() {
        when(userRepository.findByUsername("bob"))
                .thenReturn(Optional.of(user("bob", UserStatus.OPEN_TO_CHAT)));
        when(contactRepository.findByMembersContaining("alice")).thenReturn(List.of());
        when(contactRepository.save(any(Contact.class))).thenAnswer(i -> i.getArgument(0));

        Contact saved = contactService.sendRequest("alice", " bob ");

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getRequester()).isEqualTo("alice");
        assertThat(saved.getMembers()).containsExactly("alice", "bob");
        verify(contactRepository).save(any(Contact.class));
    }

    @Test
    void sendRequest_selfRequest_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> contactService.sendRequest("alice", "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yourself");
        verifyNoInteractions(contactRepository, userRepository);
    }

    @Test
    void sendRequest_existingPair_throwsIllegalArgumentException() {
        when(userRepository.findByUsername("bob"))
                .thenReturn(Optional.of(user("bob", UserStatus.OPEN_TO_CHAT)));
        Contact existing = contact("1", "alice", "bob", "alice", "PENDING");
        when(contactRepository.findByMembersContaining("alice")).thenReturn(List.of(existing));

        assertThatThrownBy(() -> contactService.sendRequest("alice", "bob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(contactRepository, never()).save(any());
    }

    @Test
    void sendRequest_unknownTarget_throwsNoSuchElementException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contactService.sendRequest("alice", "ghost"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Firewall: SLEEPING target blocks the request")
    void sendRequest_sleepingTarget_throwsSecurityException() {
        when(userRepository.findByUsername("bob"))
                .thenReturn(Optional.of(user("bob", UserStatus.SLEEPING)));

        assertThatThrownBy(() -> contactService.sendRequest("alice", "bob"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not accepting");
        verify(contactRepository, never()).save(any());
    }

    @Test
    @DisplayName("IDOR guard: requester cannot accept own request")
    void accept_byRequester_throwsSecurityException() {
        Contact pending = contact("10", "alice", "bob", "alice", "PENDING");
        when(contactRepository.findById("10")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> contactService.accept("10", "alice"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("own request");
        verify(contactRepository, never()).save(any());
    }

    @Test
    void accept_byReceiver_marksAccepted() {
        Contact pending = contact("10", "alice", "bob", "alice", "PENDING");
        when(contactRepository.findById("10")).thenReturn(Optional.of(pending));
        when(contactRepository.save(any(Contact.class))).thenAnswer(i -> i.getArgument(0));

        Contact saved = contactService.accept("10", "bob");

        assertThat(saved.getStatus()).isEqualTo("ACCEPTED");
        verify(contactRepository).save(any(Contact.class));
    }

    @Test
    void reject_byReceiver_deletesRequest() {
        Contact pending = contact("10", "alice", "bob", "alice", "PENDING");
        when(contactRepository.findById("10")).thenReturn(Optional.of(pending));

        contactService.reject("10", "bob");

        verify(contactRepository).delete(pending);
        verify(contactRepository, never()).save(any());
    }

    @Test
    void getCounts_countsAcceptedAndIncoming() {
        List<Contact> data = List.of(
                contact("1", "alice", "bob", "alice", "ACCEPTED"),
                contact("2", "alice", "carol", "carol", "PENDING"),
                contact("3", "alice", "dave", "alice", "PENDING")
        );
        when(contactRepository.findByMembersContaining("alice")).thenReturn(data);

        Map<String, Long> counts = contactService.getCounts("alice");

        assertThat(counts.get("contacts")).isEqualTo(1L);
        assertThat(counts.get("requests")).isEqualTo(1L);
    }
}
