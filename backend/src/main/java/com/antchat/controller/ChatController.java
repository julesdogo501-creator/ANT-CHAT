package com.antchat.controller;

import com.antchat.dto.ChatMessageRequest;
import com.antchat.model.Message;
import com.antchat.model.User;
import com.antchat.repository.UserRepository;
import com.antchat.service.GroqService;
import com.antchat.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChatController {


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private GroqService groqService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Message Global
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/global")
    public Message sendGlobalMessage(@Payload ChatMessageRequest chatMessageRequest) {
        User sender = userRepository.findById(chatMessageRequest.getSenderId()).orElseThrow();
        return messageService.saveGlobalMessage(sender, chatMessageRequest.getContent(), chatMessageRequest.getFileUrl(), chatMessageRequest.getFileType());
    }

    // Message Privé & Interception AntIA
    @MessageMapping("/chat.privateMessage")
    public void sendPrivateMessage(@Payload ChatMessageRequest chatMessageRequest) {
        User sender = userRepository.findById(chatMessageRequest.getSenderId()).orElseThrow();
        User receiver = userRepository.findById(chatMessageRequest.getReceiverId()).orElseThrow();
        
        System.out.println("[ChatController] Message privé de " + sender.getUsername() + " pour " + receiver.getUsername());
        
        Message savedMessage = messageService.savePrivateMessage(sender, receiver, chatMessageRequest.getContent(), chatMessageRequest.getFileUrl(), chatMessageRequest.getFileType());

        messagingTemplate.convertAndSend("/topic/private/" + receiver.getId(), savedMessage);
        messagingTemplate.convertAndSend("/topic/private/" + sender.getId(), savedMessage);

        // --- Cerveau Groq (AntIA) ---
        if ("AntIA".equals(receiver.getUsername())) {
            System.out.println("[ChatController] Déclenchement de la réponse AntIA");
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String aiResponse = groqService.generateResponse(chatMessageRequest.getContent());
                    Message aiMessage = messageService.savePrivateMessage(receiver, sender, aiResponse, null, null);
                    
                    // On notifie l'utilisateur via Websocket avec le topic privé de l'utilisateur
                    messagingTemplate.convertAndSend("/topic/private/" + sender.getId(), aiMessage);
                    System.out.println("[ChatController] Réponse AntIA envoyée à " + sender.getUsername());
                } catch (Exception e) {
                    System.err.println("[ChatController] Erreur fatale dans le thread AntIA : " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    // Message de Groupe
    @MessageMapping("/chat.groupMessage")
    public void sendGroupMessage(@Payload ChatMessageRequest chatMessageRequest) {
        User sender = userRepository.findById(chatMessageRequest.getSenderId()).orElseThrow();
        Long groupId = chatMessageRequest.getGroupId();
        
        System.out.println("[ChatController] Message de groupe pour ID: " + groupId + " par " + sender.getUsername());
        
        Message savedMessage = messageService.saveGroupMessage(sender, groupId, chatMessageRequest.getContent(), chatMessageRequest.getFileUrl(), chatMessageRequest.getFileType());

        // On broadcast à tous ceux qui écoutent le topic de ce groupe
        messagingTemplate.convertAndSend("/topic/group/" + groupId, savedMessage);
    }
}
