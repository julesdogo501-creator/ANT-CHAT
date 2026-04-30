package com.antchat.controller;

import com.antchat.model.ChatGroup;
import com.antchat.model.Message;
import com.antchat.repository.MessageRepository;
import com.antchat.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    @Autowired
    private GroupService groupService;

    @Autowired
    private MessageRepository messageRepository;

    @PostMapping
    public ChatGroup createGroup(@RequestParam String name, @RequestParam Long adminId) {
        return groupService.createGroup(name, adminId);
    }

    @GetMapping("/user/{userId}")
    public List<ChatGroup> getUserGroups(@PathVariable Long userId) {
        return groupService.getUserGroups(userId);
    }

    @PostMapping("/{groupId}/members/{userId}")
    public ChatGroup addMember(@PathVariable Long groupId, @PathVariable Long userId, @RequestParam Long requesterId) {
        return groupService.addMember(groupId, userId, requesterId);
    }

    @GetMapping("/{groupId}/history")
    public List<Message> getGroupHistory(@PathVariable Long groupId) {
        return messageRepository.findByChatGroupIdOrderByTimestampAsc(groupId);
    }
}
