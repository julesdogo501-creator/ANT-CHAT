package com.antchat.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private Long id;
    private String content;
    private User sender;
    private User receiver;
    private ChatGroup chatGroup;
    private Object timestamp; // Object pour accepter n'importe quel format du serveur (String ou Array)
    private String fileUrl;
    private String fileType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }
    public User getReceiver() { return receiver; }
    public void setReceiver(User receiver) { this.receiver = receiver; }
    public ChatGroup getChatGroup() { return chatGroup; }
    public void setChatGroup(ChatGroup chatGroup) { this.chatGroup = chatGroup; }
    public Object getTimestamp() { return timestamp; }
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
}
