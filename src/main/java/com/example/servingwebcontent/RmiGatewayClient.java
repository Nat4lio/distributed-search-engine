package com.example.servingwebcontent;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Simple bean to encapsulate RMI lookup of the Gateway.
 */
@Component
public class RmiGatewayClient {

    private final AtomicReference<GatewayInterface> gwRef = new AtomicReference<>();

    // Default settings — matches GatewayImpl main defaults
    private final String host = "localhost";
    private final int port = 1099;
    private final String serviceName = "Gateway";

    @PostConstruct
    public void init() {
        try {
            connect();
        } catch (Exception e) {
            System.err.println("[RmiGatewayClient] initial connect failed: " + e.getMessage());
        }
    }

    public synchronized GatewayInterface getGateway() throws Exception {
        GatewayInterface g = gwRef.get();
        if (g == null) {
            connect();
            g = gwRef.get();
        }
        if (g == null) throw new IllegalStateException("Gateway RMI not available");
        return g;
    }

    private void connect() throws Exception {
        Registry reg = LocateRegistry.getRegistry(host, port);
        GatewayInterface gw = (GatewayInterface) reg.lookup(serviceName);
        gwRef.set(gw);
        System.out.println("[RmiGatewayClient] connected to gateway " + host + ":" + port);
    }

    public void clear() {
        gwRef.set(null);
    }
}
