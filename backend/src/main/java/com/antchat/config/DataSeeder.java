package com.antchat.config;

import com.antchat.model.User;
import com.antchat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        Optional<User> antIa = userRepository.findByUsername("AntIA");
        if (antIa.isEmpty()) {
            User bot = new User();
            bot.setUsername("AntIA");
            bot.setPassword("1234"); // Hashed later technically, but bot doesn't login
            bot.setOnline(true);
            bot.setProfilePictureUrl("https://static.vecteezy.com/system/resources/previews/021/059/827/non_2x/chatgpt-logo-ai-artificial-intelligence-icon-free-png.png");
            userRepository.save(bot);
            System.out.println("🤖 Bot AntIA créé avec succès dans la base de données !");
        }
    }
}
