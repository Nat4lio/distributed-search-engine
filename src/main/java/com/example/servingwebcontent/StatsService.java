package com.example.servingwebcontent;

import java.rmi.RemoteException;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * StatsService: periodic task that fetches stats from Gateway (RMI) and publishes via WebSocket.
 * This implementation calls gateway.getStats(), which your GatewayImpl provides.
 */
@Service
public class StatsService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RmiGatewayClient rmiGatewayClient;

    public StatsService(SimpMessagingTemplate messagingTemplate, RmiGatewayClient rmiGatewayClient) {
        this.messagingTemplate = messagingTemplate;
        this.rmiGatewayClient = rmiGatewayClient;
    }

    /**
     * Periodically fetch stats and send to /topic/stats.
     * Interval: every 2000 ms (2s). Ajusta fixedDelay conforme necessário.
     */
    @Scheduled(fixedDelay = 2000)
    public void pushStats() {
        try {
            GatewayInterface gw = rmiGatewayClient.getGateway();
            Map<String, Object> stats = gw.getStats(); // uses GatewayImpl.getStats()
            if (stats == null) {
                messagingTemplate.convertAndSend("/topic/stats", Map.of("error", "empty-stats"));
            } else {
                messagingTemplate.convertAndSend("/topic/stats", stats);
            }
        } catch (RemoteException re) {
            messagingTemplate.convertAndSend("/topic/stats", Map.of(
                    "error", "remote-exception",
                    "message", re.getMessage()
            ));
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/stats", Map.of(
                    "error", "exception",
                    "message", e.getMessage()
            ));
        }
    }
}
