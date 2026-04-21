package com.antchat.repository;

import com.antchat.model.Message;
import com.antchat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    // Récupérer les messages du chat global
    List<Message> findByReceiverIsNullOrderByTimestampAsc();

    // Récupérer les messages privés entre deux utilisateurs
    @Query("SELECT m FROM Message m WHERE (m.sender = :user1 AND m.receiver = :user2) OR (m.sender = :user2 AND m.receiver = :user1) ORDER BY m.timestamp ASC")
    List<Message> findPrivateMessages(@Param("user1") User user1, @Param("user2") User user2);

    List<Message> findByChatGroupIdOrderByTimestampAsc(Long groupId);
}
