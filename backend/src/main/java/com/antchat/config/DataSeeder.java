package com.antchat.config;

import com.antchat.model.User;
import com.antchat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        Optional<User> antIa = userRepository.findByUsername("AntIA");
        if (antIa.isEmpty()) {
            User bot = new User();
            bot.setUsername("AntIA");
            bot.setPassword(passwordEncoder.encode("antia-bot-2024")); // Hashé correctement
            bot.setOnline(true); // Toujours en ligne
            bot.setProfilePictureUrl("https://static.vecteezy.com/system/resources/previews/021/059/827/non_2x/chatgpt-logo-ai-artificial-intelligence-icon-free-png.png");
            userRepository.save(bot);
            System.out.println("🤖 Bot AntIA créé avec succès dans la base de données !");
        } else {
            // S'assurer qu'AntIA est toujours en ligne
            User existingBot = antIa.get();
            if (!existingBot.isOnline()) {
                existingBot.setOnline(true);
                userRepository.save(existingBot);
                System.out.println("🤖 Bot AntIA remis en ligne !");
            }
        }
    }
}
