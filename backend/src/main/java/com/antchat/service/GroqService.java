package com.antchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    @org.springframework.beans.factory.annotation.Value("${groq.api.key}")
    private String API_KEY;
    private final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateResponse(String userMessage) {
        System.out.println("[GroqService] Génération d'une réponse pour : " + userMessage);
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llama-3.3-70b-versatile");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "Tu es AntIA, l'intelligence artificielle très amicale et sarcastique de l'application ANT CHAT. Réponds en français de manière courte et impactante."),
                    Map.of("role", "user", "content", userMessage)
            ));
            
            String jsonRequest = mapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = mapper.readTree(response.body());
                String content = rootNode.path("choices").get(0).path("message").path("content").asText();
                System.out.println("[GroqService] Réponse reçue avec succès : " + content);
                return content;
            } else {
                System.err.println("[GroqService] Erreur API Groq - Status: " + response.statusCode() + " - Body: " + response.body());
                return "[AntIA] Désolé, j'ai un petit bug de cerveau (Erreur " + response.statusCode() + "). Réessaie plus tard !";
            }
        } catch (Exception e) {
            System.err.println("[GroqService] Exception lors de l'appel à Groq : " + e.getMessage());
            e.printStackTrace();
            return "[AntIA] Une erreur technique m'empêche de réfléchir. Est-ce que le serveur internet est bien connecté ?";
        }
    }
}
