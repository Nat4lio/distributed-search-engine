package com.example.servingwebcontent;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SearchController {

    private GatewayInterface gateway;

    // Lazy RMI lookup — evita falhas ao arrancar
    private GatewayInterface getGateway() {
        if (gateway == null) {
            try {
                Registry reg = LocateRegistry.getRegistry("localhost", 1099);
                gateway = (GatewayInterface) reg.lookup("Gateway");
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to the RMI gateway.", e);
            }
        }
        return gateway;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/search")
    public String search(@RequestParam("q") String query, Model model) throws Exception {

        // lookup seguro
        List<SearchResult> results =
                getGateway().search(List.of(query), 1, 10);

        model.addAttribute("query", query);
        model.addAttribute("results", results);
        return "results";
    }
}