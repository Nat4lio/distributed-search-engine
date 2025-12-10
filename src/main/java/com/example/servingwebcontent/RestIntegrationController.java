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

/**
 * Controller REST para integrações externas:
 * - /api/index-hn  -> indexar URLs do HackerNews
 * - /api/openai    -> gerar análise com OpenAI
 */
@RestController
@RequestMapping("/api")
public class RestIntegrationController {

    private final HackerNewsService hnService;
    private final OpenAIService openAIService;
    private final RmiGatewayClient rmiGatewayClient;

    public RestIntegrationController(HackerNewsService hnService, OpenAIService openAIService, RmiGatewayClient rmiGatewayClient) {
        this.hnService = hnService;
        this.openAIService = openAIService;
        this.rmiGatewayClient = rmiGatewayClient;
    }

    /**
     * Indexa URLs do Hacker News que contenham os termos.
     * Body esperado: {"query":"termos", "maxCheck":100}
     */
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
                } catch (RemoteException re) {
                    // continue; não queremos falhar tudo por 1 url
                }
            }
            Map<String,Object> res = new HashMap<>();
            res.put("found", urls.size());
            res.put("enqueued", enqueued);
            res.put("urls", urls);
            return ResponseEntity.ok(res);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Gera análise com OpenAI.
     * Body esperado: {"query":"...","snippets":"..."}
     */
    @PostMapping("/openai")
    public ResponseEntity<?> openAiAnalysis(@RequestBody Map<String, Object> body) {
        String query = (body.get("query") == null) ? "" : body.get("query").toString();
        String snippets = (body.get("snippets") == null) ? "" : body.get("snippets").toString();

        try {
            if (!openAIService.hasApiKey()) {
                return ResponseEntity.status(400).body(Map.of("error", "OPENAI_API_KEY not set"));
            }

            String prompt = "Faça uma análise breve e contextualizada para a query: \"" + query + "\".\n\n" +
                    "Contexto / snippets:\n" + snippets + "\n\n" +
                    "Resuma pontos importantes e indique se os resultados parecem relevantes.";
            String analysis = openAIService.generateAnalysis(prompt);
            return ResponseEntity.ok(Map.of("analysis", analysis));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
