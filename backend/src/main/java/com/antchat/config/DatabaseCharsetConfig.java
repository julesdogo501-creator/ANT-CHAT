package com.antchat.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCharsetConfig {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            System.out.println("🔧 Conversion de la base de données pour supporter les emojis (utf8mb4)...");
            
            // On convertit la table messages
            jdbcTemplate.execute("ALTER TABLE messages CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            
            // On convertit aussi le contenu pour être sûr
            jdbcTemplate.execute("ALTER TABLE messages MODIFY content TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            
            System.out.println("✅ Conversion réussie !");
        } catch (Exception e) {
            System.err.println("⚠️ Erreur lors de la conversion du charset : " + e.getMessage());
            // Souvent c'est parce que la table n'existe pas encore au tout premier lancement
        }
    }
}
