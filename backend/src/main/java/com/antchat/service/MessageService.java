package com.antchat.service;

import com.antchat.model.ChatGroup;
import com.antchat.model.Message;
import com.antchat.model.User;
import com.antchat.repository.ChatGroupRepository;
import com.antchat.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatGroupRepository groupRepository;

    public Message saveGlobalMessage(User sender, String content, String fileUrl, String fileType) {
        Message message = new Message();
        message.setContent(content);
        message.setSender(sender);
        message.setFileUrl(fileUrl);
        message.setFileType(fileType);
        message.setTimestamp(LocalDateTime.now());
        return messageRepository.save(message); // Sauvegarde automatique via l'ORM Spring
    }

    public Message savePrivateMessage(User sender, User receiver, String content, String fileUrl, String fileType) {
        Message message = new Message();
        message.setContent(content);
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setFileUrl(fileUrl);
        message.setFileType(fileType);
        message.setTimestamp(LocalDateTime.now());
        return messageRepository.save(message); // Sauvegarde automatique via l'ORM Spring
    }

    public Message saveGroupMessage(User sender, Long groupId, String content, String fileUrl, String fileType) {
        ChatGroup group = groupRepository.findById(groupId).orElseThrow();
        Message message = new Message();
        message.setContent(content);
        message.setSender(sender);
        message.setChatGroup(group);
        message.setFileUrl(fileUrl);
        message.setFileType(fileType);
        message.setTimestamp(LocalDateTime.now());
        return messageRepository.save(message);
    }
}
