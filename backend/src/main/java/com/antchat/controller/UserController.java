package com.antchat.controller;

import com.antchat.model.Message;
import com.antchat.model.User;
import com.antchat.repository.MessageRepository;
import com.antchat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MessageRepository messageRepository;

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updates) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        
        if (updates.getUsername() != null && !updates.getUsername().trim().isEmpty()) {
            user.setUsername(updates.getUsername().trim());
        }
        if (updates.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(updates.getProfilePictureUrl());
        }
        
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/history/global")
    public List<Message> getGlobalHistory() {
        return messageRepository.findByReceiverIsNullOrderByTimestampAsc();
    }

    @GetMapping("/history/private/{user1Id}/{user2Id}")
    public ResponseEntity<?> getPrivateHistory(@PathVariable Long user1Id, @PathVariable Long user2Id) {
        User u1 = userRepository.findById(user1Id).orElse(null);
        User u2 = userRepository.findById(user2Id).orElse(null);
        if (u1 == null || u2 == null) return ResponseEntity.badRequest().build();
        
        return ResponseEntity.ok(messageRepository.findPrivateMessages(u1, u2));
    }
}
