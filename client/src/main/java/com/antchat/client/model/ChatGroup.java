package com.antchat.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashSet;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatGroup {
    private Long id;
    private String name;
    private String description;
    private String groupPictureUrl;
    private User admin;
    private Set<User> members = new HashSet<>();

    public ChatGroup() {}

    public ChatGroup(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGroupPictureUrl() { return groupPictureUrl; }
    public void setGroupPictureUrl(String groupPictureUrl) { this.groupPictureUrl = groupPictureUrl; }

    public User getAdmin() { return admin; }
    public void setAdmin(User admin) { this.admin = admin; }

    public Set<User> getMembers() { return members; }
    public void setMembers(Set<User> members) { this.members = members; }
    
    @Override
    public String toString() {
        return name;
    }
}
