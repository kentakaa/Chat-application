package com.arshad.chat_backend.service;

import com.arshad.chat_backend.entity.Contact;
import com.arshad.chat_backend.entity.User;
import com.arshad.chat_backend.entity.UserStatus;
import com.arshad.chat_backend.repository.ContactRepository;
import com.arshad.chat_backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ContactService {

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private UserRepository userRepository;

    public Contact sendRequest(String from, String to) {
        if (from == null || to == null || to.isBlank()) {
            throw new IllegalArgumentException("Target username is required");
        }
        
        final String targetName = to.trim();

        if (from.equalsIgnoreCase(targetName)) {
            throw new IllegalArgumentException("You cannot add yourself as a contact");
        }

        // Target must exist → 404
        User target = userRepository.findByUsername(targetName)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + targetName));

        // FIREWALL: SLEEPING users don't accept requests → 403
        if (target.getStatus() == UserStatus.SLEEPING) {
            throw new SecurityException(target.getUsername() + " is not accepting requests right now");
        }

        // Duplicate pair guard (PENDING ya ACCEPTED dono block) → 400
        boolean pairExists = contactRepository.findByMembersContaining(from).stream()
                .anyMatch(c -> c.getMembers().stream()
                        .anyMatch(m -> m.equalsIgnoreCase(targetName)));
        if (pairExists) {
            throw new IllegalArgumentException("A request already exists with " + targetName);
        }

        // Deterministic pair (DM room jaisa): sorted [a, b]
        List<String> members = new ArrayList<>(List.of(from, targetName));
        Collections.sort(members);

        Contact contact = new Contact();
        contact.setMembers(members);
        contact.setRequester(from);
        contact.setStatus("PENDING");
        contact.setCreatedAt(LocalDateTime.now());

        Contact saved = contactRepository.save(contact);
        log.info("Contact request sent: {} -> {}", from, to);
        return saved;
    }

    // ==========================================
    // 2. ACCEPT (sirf receiver — IDOR guard)
    // ==========================================
    public Contact accept(String contactId, String user) {
        Contact contact = requirePendingRequestOfReceiver(contactId, user);
        contact.setStatus("ACCEPTED");
        Contact saved = contactRepository.save(contact);
        log.info("Contact request {} accepted by {}", contactId, user);
        return saved;
    }

    // ==========================================
    // 3. REJECT (receiver only → delete, dobara bhej sakte ho)
    // ==========================================
    public void reject(String contactId, String user) {
        Contact contact = requirePendingRequestOfReceiver(contactId, user);
        contactRepository.delete(contact);
        log.info("Contact request {} rejected by {}", contactId, user);
    }

    // Shared guard: accept + reject dono ke liye (IDOR protection)
    private Contact requirePendingRequestOfReceiver(String contactId, String user) {
        if (contactId == null || contactId.isBlank()) {
            throw new IllegalArgumentException("Request id is required");
        }
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new NoSuchElementException("Request not found"));

        if (!"PENDING".equals(contact.getStatus())) {
            throw new IllegalArgumentException("Request is no longer pending");
        }
        if (contact.getMembers().stream().noneMatch(m -> m.equalsIgnoreCase(user))) {
            throw new SecurityException("You are not part of this request");
        }
        if (contact.getRequester().equalsIgnoreCase(user)) {
            throw new SecurityException("You cannot respond to your own request");
        }
        return contact;
    }

    // ==========================================
    // 4. ACCEPTED CONTACTS
    // ==========================================
    public List<Contact> getContacts(String user) {
        return contactRepository.findByMembersContaining(user).stream()
                .filter(c -> "ACCEPTED".equals(c.getStatus()))
                .toList();
    }

    // ==========================================
    // 5. PENDING REQUESTS (incoming + outgoing, ek hi query)
    // ==========================================
    public Map<String, List<Contact>> getRequests(String user) {
        List<Contact> all = contactRepository.findByMembersContaining(user);

        List<Contact> incoming = all.stream()
                .filter(c -> "PENDING".equals(c.getStatus()))
                .filter(c -> !c.getRequester().equalsIgnoreCase(user))
                .toList();

        List<Contact> outgoing = all.stream()
                .filter(c -> "PENDING".equals(c.getStatus()))
                .filter(c -> c.getRequester().equalsIgnoreCase(user))
                .toList();

        Map<String, List<Contact>> result = new LinkedHashMap<>();
        result.put("incoming", incoming);
        result.put("outgoing", outgoing);
        return result;
    }

    // ==========================================
    // 6. PROFILE COUNTS (fake 12/3 replace karne ke liye)
    // ==========================================
    public Map<String, Long> getCounts(String user) {
        List<Contact> all = contactRepository.findByMembersContaining(user);

        long contacts = all.stream()
                .filter(c -> "ACCEPTED".equals(c.getStatus()))
                .count();

        long requests = all.stream()
                .filter(c -> "PENDING".equals(c.getStatus()))
                .filter(c -> !c.getRequester().equalsIgnoreCase(user))
                .count();

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("contacts", contacts);
        counts.put("requests", requests);
        return counts;
    }

    // ==========================================
    // HELPER: contact me se doosra member nikalo (UI ke liye)
    // ==========================================
    public String otherMember(Contact contact, String user) {
        return contact.getMembers().stream()
                .filter(m -> !m.equalsIgnoreCase(user))
                .findFirst()
                .orElse("unknown");
    }
}