package com.antchat.client.model;

public class ChatItem {
    private User user;
    private ChatGroup group;
    private boolean isGroup;

    public ChatItem(User user) {
        this.user = user;
        this.isGroup = false;
    }

    public ChatItem(ChatGroup group) {
        this.group = group;
        this.isGroup = true;
    }

    public User getUser() { return user; }
    public ChatGroup getGroup() { return group; }
    public boolean isGroup() { return isGroup; }

    public String getName() {
        return isGroup ? group.getName() : user.getUsername();
    }

    public String getAvatarUrl() {
        return isGroup ? group.getGroupPictureUrl() : user.getProfilePictureUrl();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChatItem other = (ChatItem) obj;
        if (isGroup != other.isGroup) return false;
        
        if (isGroup) {
            return java.util.Objects.equals(group.getId(), other.group.getId());
        } else {
            // Pour le Chat Global (ID null), on compare par pseudonyme
            if (user.getId() == null || other.user.getId() == null) {
                return java.util.Objects.equals(user.getUsername(), other.user.getUsername());
            }
            return java.util.Objects.equals(user.getId(), other.user.getId());
        }
    }

    @Override
    public int hashCode() {
        if (isGroup) return java.util.Objects.hashCode(group.getId());
        if (user.getId() == null) return java.util.Objects.hashCode(user.getUsername());
        return java.util.Objects.hashCode(user.getId());
    }
}
