package com.antchat.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "chat_groups")
@Data
@NoArgsConstructor
public class ChatGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;
    
    @Column(columnDefinition = "LONGTEXT")
    private String groupPictureUrl;

    @ManyToOne
    @JoinColumn(name = "admin_id")
    private User admin; // Le créateur du groupe

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "group_members",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> members = new HashSet<>();

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Set<User> getMembers() { return members; }
    public void setMembers(Set<User> members) { this.members = members; }

    public User getAdmin() { return admin; }
    public void setAdmin(User admin) { this.admin = admin; }
    
    public String getGroupPictureUrl() { return groupPictureUrl; }
    public void setGroupPictureUrl(String groupPictureUrl) { this.groupPictureUrl = groupPictureUrl; }
}
