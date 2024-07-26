package com.aisip.OnO.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class mainController {

    @GetMapping("/")
    public ResponseEntity<?> mainPage(){
        return ResponseEntity.ok().body("this is spring main page");
    }
}
