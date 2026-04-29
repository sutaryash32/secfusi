package com.secufusion.api_gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FallbackController {
    @RequestMapping("/fallback")
    public ResponseEntity<Map<String,Object>> fallback() {
        Map<String,Object> body = Map.of(
                "message","Service temporarily unavailable. Please try again later.",
                "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.status(503).body(body);
    }
}
