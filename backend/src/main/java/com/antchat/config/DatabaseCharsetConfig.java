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
            // Vérification préalable : la table doit exister (pas le cas au 1er lancement)
            Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'messages'",
                Integer.class
            );
            if (tableCount == null || tableCount == 0) {
                System.out.println("ℹ️ Table 'messages' pas encore créée, charset conversion ignorée (sera faite au prochain démarrage).");
                return;
            }

            System.out.println("🔧 Vérification du charset utf8mb4 sur la table messages...");
            jdbcTemplate.execute("ALTER TABLE messages CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            jdbcTemplate.execute("ALTER TABLE messages MODIFY content TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            System.out.println("✅ Charset utf8mb4 OK !");
        } catch (Exception e) {
            // Non bloquant : si déjà utf8mb4, MySQL retourne une erreur ignorable
            System.out.println("ℹ️ Charset conversion ignorée : " + e.getMessage());
        }
    }
}
