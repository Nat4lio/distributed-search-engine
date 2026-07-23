package com.example.servingwebcontent;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RestIntegrationController {

    private final HackerNewsService hnService;
    private final OpenAIService openAIService;
    private final RmiGatewayClient rmiGatewayClient;

    public RestIntegrationController(HackerNewsService hnService,
                                    OpenAIService openAIService,
                                    RmiGatewayClient rmiGatewayClient) {
        this.hnService = hnService;
        this.openAIService = openAIService;
        this.rmiGatewayClient = rmiGatewayClient;
    }

    // ----------------------------------------------------------------------
    // INDEXAR VIA HACKER NEWS
    // ----------------------------------------------------------------------

    @PostMapping("/index-hn")
    public ResponseEntity<?> indexFromHackerNews(@RequestBody Map<String, Object> body) {
        String query = (body.get("query") == null) ? "" : body.get("query").toString();
        int maxCheck = body.get("maxCheck") == null ? 100 : Integer.parseInt(body.get("maxCheck").toString());

        try {
            List<String> urls = hnService.findTopStoriesContaining(query, maxCheck);
            int enqueued = 0;

            GatewayInterface gw = rmiGatewayClient.getGateway();
            for (String url : urls) {
                try {
                    boolean ok = gw.indexPage(url);
                    if (ok) enqueued++;
                } catch (RemoteException ignored) {}
            }

            Map<String, Object> res = new HashMap<>();
            res.put("found", urls.size());
            res.put("enqueued", enqueued);
            res.put("urls", urls);

            return ResponseEntity.ok(res);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ----------------------------------------------------------------------
    // IA FREE OU OPENAI (dependendo se tens chave)
    // ----------------------------------------------------------------------

    @PostMapping("/openai")
    public ResponseEntity<?> openAiAnalysis(@RequestBody Map<String, Object> body) {

        String query = body.getOrDefault("query", "").toString();
        String snippets = body.getOrDefault("snippets", "").toString();

        try {
            // construir prompt final
            String prompt = 
                    "Faça uma análise breve e contextualizada para a pesquisa: \"" + query + "\".\n\n" +
                    "Resultados obtidos:\n" + snippets + "\n\n" +
                    "Resuma os pontos principais e indique a relevância geral.";

            // chama a IA correta (OpenAI se tiver key, HuggingFace se não tiver)
            String analysis = openAIService.generateAnalysis(prompt);

            return ResponseEntity.ok(Map.of(
                    "provider", openAIService.hasApiKey() ? "openai" : "huggingface",
                    "analysis", analysis
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}
