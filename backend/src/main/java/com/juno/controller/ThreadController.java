package com.juno.controller;

import com.juno.persistence.entity.MessageEntity;
import com.juno.persistence.entity.ThreadEntity;
import com.juno.service.ThreadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/threads")
@CrossOrigin(origins = "*")
public class ThreadController {

    private final ThreadService threadService;

    public ThreadController(ThreadService threadService) {
        this.threadService = threadService;
    }

    @GetMapping
    public List<ThreadEntity> listThreads(@RequestParam(defaultValue = "default") String userId) {
        return threadService.getThreadsForUser(userId);
    }

    @PostMapping
    public ThreadEntity createThread(@RequestBody(required = false) Map<String, String> body) {
        String userId = body != null ? body.getOrDefault("userId", "default") : "default";
        return threadService.createThread(userId);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ThreadEntity> updateThread(@PathVariable String id,
                                                     @RequestBody Map<String, String> body) {
        if (body.containsKey("title")) {
            var thread = threadService.updateTitle(id, body.get("title"));
            return thread != null ? ResponseEntity.ok(thread) : ResponseEntity.notFound().build();
        }
        if (body.containsKey("previewText")) {
            var thread = threadService.updateLastMessage(id, body.get("previewText"));
            return thread != null ? ResponseEntity.ok(thread) : ResponseEntity.notFound().build();
        }
        return ResponseEntity.badRequest().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteThread(@PathVariable String id) {
        threadService.deleteThread(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public List<MessageEntity> getMessages(@PathVariable String id) {
        return threadService.getMessages(id);
    }

    @PostMapping("/{id}/messages")
    public MessageEntity addMessage(@PathVariable String id,
                                    @RequestBody Map<String, String> body) {
        return threadService.addMessage(id,
                body.getOrDefault("role", "user"),
                body.getOrDefault("content", ""));
    }
}
