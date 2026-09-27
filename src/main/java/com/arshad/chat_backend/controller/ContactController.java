package com.arshad.chat_backend.controller;

import com.arshad.chat_backend.entity.Contact;
import com.arshad.chat_backend.service.ContactService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    @Autowired
    private ContactService contactService;

    private String msg(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : "Invalid request";
    }

    private Map<String, Object> toMap(Contact contact, String user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", contact.getId());
        m.put("username", contactService.otherMember(contact, user));
        m.put("requester", contact.getRequester());
        m.put("status", contact.getStatus());
        m.put("createdAt", contact.getCreatedAt());
        return m;
    }

    // 1. SEND REQUEST: POST /api/contacts/request  body: {"username":"bob"}
    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(@RequestBody Map<String, String> body, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        try {
            Contact contact = contactService.sendRequest(principal.getName(), body.get("username"));
            return ResponseEntity.status(201).body(contact);
        } catch (IllegalArgumentException e) {
            log.warn("Contact request rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", msg(e)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("message", msg(e)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", msg(e)));
        }
    }

    // 2. LIST: GET /api/contacts
    @GetMapping
    public ResponseEntity<?> getContacts(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        String user = principal.getName();
        List<Map<String, Object>> result = contactService.getContacts(user).stream()
                .map(c -> toMap(c, user))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // 3. REQUESTS: GET /api/contacts/requests → {incoming:[], outgoing:[]}
    @GetMapping("/requests")
    public ResponseEntity<?> getRequests(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        String user = principal.getName();
        Map<String, List<Contact>> requests = contactService.getRequests(user);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("incoming", requests.get("incoming").stream()
                .map(c -> toMap(c, user)).collect(Collectors.toList()));
        body.put("outgoing", requests.get("outgoing").stream()
                .map(c -> toMap(c, user)).collect(Collectors.toList()));
        return ResponseEntity.ok(body);
    }

    // 4. ACCEPT: POST /api/contacts/{id}/accept
    @PostMapping("/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable String id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        try {
            Contact contact = contactService.accept(id, principal.getName());
            return ResponseEntity.ok(toMap(contact, principal.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", msg(e)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("message", msg(e)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", msg(e)));
        }
    }

    // 5. REJECT: POST /api/contacts/{id}/reject  (doc delete hota hai)
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable String id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        try {
            contactService.reject(id, principal.getName());
            return ResponseEntity.ok(Map.of("message", "Request rejected"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", msg(e)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("message", msg(e)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", msg(e)));
        }
    }
}