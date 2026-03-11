package com.aisip.OnO.backend.common.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class mainController {

    @GetMapping({"/", "/home"})
    public String mainPage() {
        return "home";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping({
            "/{path:^(?!api$|admin$|swagger-ui$|v3$|actuator$|grafana$|prometheus$|images$|css$|js$|home$|login$|robots\\.txt$).*$}",
            "/{path:^(?!api$|admin$|swagger-ui$|v3$|actuator$|grafana$|prometheus$|images$|css$|js$|home$|login$|robots\\.txt$).*$}/**"
    })
    public String redirectUnknownGetPathToHome() {
        return "redirect:/";
    }
}
