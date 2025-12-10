package com.example.servingwebcontent;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller que serve a página /stats (Thymeleaf template "stats.html").
 */
@Controller
public class StatsWebController {

    @GetMapping("/stats")
    public String statsPage() {
        return "stats";
    }
}
