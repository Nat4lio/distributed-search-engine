package com.example.servingwebcontent;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class IndexController {

    private GatewayInterface gateway;

    // Lazy RMI lookup (só quando for preciso)
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

    @GetMapping("/index")
    public String indexForm() {
        return "indexurl"; // mostra o formulário
    }

    @PostMapping("/index")
    public String submitIndex(@RequestParam("url") String url, Model model) throws Exception {

        // -> AQUI usamos o método seguro getGateway()
        getGateway().indexPage(new PageInfo(url, "", ""), null);

        model.addAttribute("url", url);
        return "indexed";
    }
}