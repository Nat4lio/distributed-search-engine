package com.example.servingwebcontent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpenAIService {

    private final String apiKey;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAIService() {
        this.apiKey = System.getenv("OPENAI_API_KEY"); 
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * PONTO PRINCIPAL:
     * - Se existir chave OpenAI → usa OpenAI
     * - Senão → usa HuggingFace FLAN-T5 Base (gratuito, sem key)
     * - Se falhar → fallback local simples
     */
    public String generateAnalysis(String prompt) throws Exception {
        if (hasApiKey()) {
            try {
                return callOpenAI(prompt);
            } catch (Exception e) {
                // continua para fallback gratuito
            }
        }

        try {
            return callHuggingFace(prompt);
        } catch (Exception e) {
            // fallback final
            return "[Fallback Local]\nTermos analisados:\n" + prompt;
        }
    }

    // =============================================================
    // 1) CHAMADA OFICIAL À OPENAI (SE TIVER KEY)
    // =============================================================
    private String callOpenAI(String prompt) throws Exception {

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        List<Map<String,String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a helpful summarizer."));
        messages.add(Map.of("role", "user", "content", prompt));
        body.put("messages", messages);
        body.put("max_tokens", 300);

        String json = mapper.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 400)
            throw new RuntimeException("OpenAI error: " + resp.body());

        JsonNode root = mapper.readTree(resp.body());
        return root.path("choices").get(0).path("message").path("content").asText();
    }


    // =============================================================
    // 2) IA GRATUITA — HuggingFace FLAN-T5 BASE
    // =============================================================
    private String callHuggingFace(String prompt) throws Exception {

        Map<String, Object> body = Map.of(
                "inputs", "Summarize this text:\n\n" + prompt
        );

        String json = mapper.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api-inference.huggingface.co/models/google/flan-t5-base"))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 400)
            throw new RuntimeException("HuggingFace error: " + resp.body());

        JsonNode root = mapper.readTree(resp.body());

        if (root.isArray() && root.size() > 0) {
            return root.get(0).path("generated_text").asText();
        }

        return "Resumo não disponível.";
    }

}

