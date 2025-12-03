package com.example.servingwebcontent;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class base {
    @GetMapping("/")
    public String home() {
        return "base";
    }
}
