package com.dodgeai.graph_system.controller;


import com.dodgeai.graph_system.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private GeminiService geminiService;

    // Send a natural language question
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(
            @RequestBody Map<String, String> body) {

        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("answer", "Please provide a message.",
                            "blocked", false));
        }

        return ResponseEntity.ok(geminiService.query(message));
    }

    // Clear conversation memory
    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clear() {
        geminiService.clearHistory();
        return ResponseEntity.ok(
                Map.of("message", "Conversation cleared."));
    }
}

