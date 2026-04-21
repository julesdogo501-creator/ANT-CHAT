package com.antchat.service;

import com.antchat.model.ChatGroup;
import com.antchat.model.User;
import com.antchat.repository.ChatGroupRepository;
import com.antchat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class GroupService {

    @Autowired
    private ChatGroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    public ChatGroup createGroup(String name, Long adminId) {
        User admin = userRepository.findById(adminId).orElseThrow();
        ChatGroup group = new ChatGroup();
        group.setName(name);
        group.setAdmin(admin);
        group.getMembers().add(admin);
        return groupRepository.save(group);
    }

    public List<ChatGroup> getUserGroups(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return groupRepository.findByMembersContaining(user);
    }

    public ChatGroup addMember(Long groupId, Long userId) {
        ChatGroup group = groupRepository.findById(groupId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        group.getMembers().add(user);
        return groupRepository.save(group);
    }
}
