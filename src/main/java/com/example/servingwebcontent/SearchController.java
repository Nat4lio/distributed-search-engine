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

    private GatewayInterface getGateway() throws Exception {
        Registry reg = LocateRegistry.getRegistry("localhost", 1099);
        return (GatewayInterface) reg.lookup("Gateway");
    }

    @GetMapping("/search")
    public String searchForm() {
        return "index";
    }

    @GetMapping("/results")
    public String search(@RequestParam("q") String query, Model model) throws Exception {

        List<SearchResult> results = getGateway().search(List.of(query), 1, 10);

        model.addAttribute("query", query);
        model.addAttribute("results", results);

        return "results";
    }
}