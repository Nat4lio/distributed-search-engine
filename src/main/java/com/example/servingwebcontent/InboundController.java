package com.example.servingwebcontent;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class InboundController {

    private GatewayInterface getGateway() throws Exception {
        Registry reg = LocateRegistry.getRegistry("localhost", 1099);
        return (GatewayInterface) reg.lookup("Gateway");
    }

    @GetMapping("/inlinks")
    public String inlinks(@RequestParam(value="url", required=false) String url, Model model) throws Exception {

        if (url != null) {
            Set<String> inlinks = getGateway().inboundLinks(url);
            model.addAttribute("links", inlinks);
            model.addAttribute("url", url);
        }

        return "inlinks";
    }
}