package com.example.servingwebcontent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

@SpringBootApplication
@EnableScheduling
public class ServingWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServingWebApplication.class, args);
    }

    
    // Bean do StatsService, recebe MessageService como dependência
    @Bean
    public StatsService statsService(SimpMessagingTemplate messagingTemplate) {
        return new StatsService(messagingTemplate);
    }

    // Bean do Gateway, recebe StatsService como dependência
    @Bean
    public GatewayImpl gateway(StatsService statsService) throws Exception {
        GatewayImpl gateway = new GatewayImpl();
        gateway.setStatsService(statsService);

        // Registo no RMI (opcional)
        try {
            Registry registry;
            try {
                registry = LocateRegistry.getRegistry(1099);
                registry.list(); // testa se já existe
            } catch (Exception e) {
                registry = LocateRegistry.createRegistry(1099); // cria se não existir
            }

            registry.rebind("Gateway", gateway);
            System.out.println("[Spring] Gateway iniciado e StatsService associado via RMI.");
        } catch (Exception e) {
            System.err.println("[Spring] Falha ao registar Gateway no RMI: " + e.getMessage());
        }

        return gateway;
    }
}