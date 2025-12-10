package com.example.servingwebcontent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Serviço para consultar Hacker News (Firebase API).
 * findTopStoriesContaining(term, maxToCheck) -> lista de URLs dos items cujo title/text contenham 'term'.
 */
@Service
public class HackerNewsService {

    private final RestTemplate rest = new RestTemplate();
    private final String topStoriesUrl = "https://hacker-news.firebaseio.com/v0/topstories.json";
    private final String itemUrlFmt = "https://hacker-news.firebaseio.com/v0/item/%d.json";

    /**
     * Procura nas topstories por histórias que contenham 'term' no title ou text.
     * maxToCheck limita quantos ids de topstories vão ser verificados (por performance).
     */
    public List<String> findTopStoriesContaining(String term, int maxToCheck) {
        List<String> found = new ArrayList<>();
        if (term == null || term.isBlank()) return found;
        try {
            Integer[] ids = rest.getForObject(topStoriesUrl, Integer[].class);
            if (ids == null) return found;
            int count = 0;
            String q = term.toLowerCase();
            for (int id : ids) {
                if (count++ >= maxToCheck) break;
                Map<?,?> item = rest.getForObject(String.format(itemUrlFmt, id), Map.class);
                if (item == null) continue;
                Object title = item.get("title");
                Object text = item.get("text");
                if ((title != null && title.toString().toLowerCase().contains(q)) ||
                    (text != null && text.toString().toLowerCase().contains(q))) {
                    Object url = item.get("url");
                    if (url != null) found.add(url.toString());
                }
            }
        } catch (Exception e) {
            // em caso de erro devolvemos lista vazia (o controller reportará a falha)
            System.err.println("[HackerNewsService] error: " + e.getMessage());
        }
        return found;
    }
}
