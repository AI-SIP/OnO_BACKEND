package com.aisip.OnO.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class mainController {

    @GetMapping("/")
    public ResponseEntity<?> mainPage() {
        String redirectUrl = "https://semnisem.notion.site/MVP-e104fd6af0064941acf464e6f77eabb3";
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
