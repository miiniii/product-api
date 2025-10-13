package com.flab.testrepojava.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {
    @GetMapping("/")
    public String rootHealth() {
        return "API root is running!";
    }
}
