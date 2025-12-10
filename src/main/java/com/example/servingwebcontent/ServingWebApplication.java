package com.example.servingwebcontent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServingWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServingWebApplication.class, args);
    }
}
