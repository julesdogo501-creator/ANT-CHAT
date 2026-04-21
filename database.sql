-- Création de la base de données
CREATE DATABASE IF NOT EXISTS antchat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE antchat_db;

-- Table des utilisateurs (avec mots de passe hachés)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL, -- Assez long pour accueillir le hash BCrypt (généralement 60 caractères)
    is_online BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table des messages (Gère le chat Global et Privé)
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT DEFAULT NULL, -- NULL = Message envoyé dans le chat Global, NOT NULL = Message Privé
    
    -- Clés étrangères
    CONSTRAINT fk_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
);
