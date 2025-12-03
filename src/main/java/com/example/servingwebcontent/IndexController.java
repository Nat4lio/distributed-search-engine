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

    private GatewayInterface getGateway() throws Exception {
        Registry reg = LocateRegistry.getRegistry("localhost", 1099);
        return (GatewayInterface) reg.lookup("Gateway");
    }

    @GetMapping("/index")
    public String indexForm() {
        return "indexurl";
    }

    @PostMapping("/index")
    public String submitIndex(@RequestParam("url") String url, Model model) throws Exception {
        getGateway().indexPage(new PageInfo(url, "", ""), null);
        model.addAttribute("url", url);
        return "indexed";
    }
}