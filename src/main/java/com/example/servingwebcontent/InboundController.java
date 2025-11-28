package com.example.servingwebcontent;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Set;

@Controller
public class InboundController {

    private GatewayImpl gateway;

    // Lookup RMI apenas quando necessário
    private GatewayImpl getGateway() {
        if (gateway == null) {
            try {
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                gateway = (GatewayImpl) registry.lookup("gateway");
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to the RMI gateway.", e);
            }
        }
        return gateway;
    }

    @GetMapping("/inlinks")
    public String inlinks(@RequestParam("url") String url, Model model) throws Exception {

        // Usa getGateway() — evita NullPointer e falhas no arranque
        Set<String> inlinks = getGateway().inboundLinks(url);

        model.addAttribute("url", url);
        model.addAttribute("links", inlinks);

        return "inlinks";
    }
}