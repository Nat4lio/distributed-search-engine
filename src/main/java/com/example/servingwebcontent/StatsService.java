package com.example.servingwebcontent;

import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * StatsService: periodic task that fetches stats from Gateway (RMI) and publishes via WebSocket.
 * This implementation calls gateway.getStats(), which your GatewayImpl provides.
 */
@Service
public class StatsService  {

    private final SimpMessagingTemplate messagingTemplate;

    public StatsService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void pushStats(Map<String, Object> stats) {
        try {
            if (stats == null) {
                messagingTemplate.convertAndSend("/topic/stats", Map.of("error", "empty-stats"));
            } else {
                messagingTemplate.convertAndSend("/topic/stats", stats);
            }
        } 
            catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/stats", Map.of(
                    "error", "exception",
                    "message", e.getMessage() 
            ));
        }
    }
}
