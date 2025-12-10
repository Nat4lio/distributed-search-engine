package com.example.servingwebcontent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serviço simples para chamar a API de Chat Completions da OpenAI.
 * Lê chave da variável de ambiente OPENAI_API_KEY.
 *
 * Nota: este código usa o endpoint "v1/chat/completions". Ajusta o modelo se necessário.
 */
@Service
public class OpenAIService {

    private final String apiKey;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAIService() {
        this.apiKey = System.getenv("OPENAI_API_KEY"); // segura: não colocamos chave no código
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Gera uma análise textual dado um prompt (já construído pelo controller).
     * Retorna a string com o texto gerado ou lança Exception com mensagem amigável.
     */
    public String generateAnalysis(String prompt) throws Exception {
        if (!hasApiKey()) throw new IllegalStateException("OPENAI_API_KEY not set");

        // Construir body
        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini"); // sugestão; podes mudar para o modelo da tua conta
        List<Map<String,String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a helpful search summarizer."));
        messages.add(Map.of("role", "user", "content", prompt));
        body.put("messages", messages);
        body.put("max_tokens", 450);

        String bodyJson = mapper.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 400) {
            throw new RuntimeException("OpenAI API error: HTTP " + resp.statusCode() + " - " + resp.body());
        }

        JsonNode root = mapper.readTree(resp.body());
        // path defensivo: choices[0].message.content
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode msg = choices.get(0).path("message").path("content");
            if (!msg.isMissingNode()) return msg.asText();
        }

        // fallback: tenta 'text' (modelos legacy)
        JsonNode text = root.path("choices").path(0).path("text");
        if (!text.isMissingNode()) return text.asText();

        throw new RuntimeException("OpenAI: unexpected response format");
    }
}
