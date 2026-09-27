package com.arshad.chat_backend.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.arshad.chat_backend.entity.Contact;

@Repository 
public interface ContactRepository extends MongoRepository<Contact, String>{
    List<Contact> findByMembersContaining(String username);
    }

