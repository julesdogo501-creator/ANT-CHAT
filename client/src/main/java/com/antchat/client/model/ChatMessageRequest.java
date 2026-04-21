package com.antchat.client.model;

public class ChatMessageRequest {
    private String content;
    private Long senderId;
    private Long receiverId;
    private Long groupId;
    private String fileUrl;
    private String fileType;

    public ChatMessageRequest() {}
    public ChatMessageRequest(String content, Long senderId, Long receiverId) {
        this.content = content;
        this.senderId = senderId;
        this.receiverId = receiverId;
    }

    public ChatMessageRequest(String content, Long senderId, Long receiverId, String fileUrl, String fileType) {
        this.content = content;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
    }

    public ChatMessageRequest(String content, Long senderId, Long receiverId, Long groupId, String fileUrl, String fileType) {
        this.content = content;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.groupId = groupId;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
    }

    public String getContent() { return content; }
    public Long getSenderId() { return senderId; }
    public Long getReceiverId() { return receiverId; }
    public Long getGroupId() { return groupId; }
    public String getFileUrl() { return fileUrl; }
    public String getFileType() { return fileType; }
}
