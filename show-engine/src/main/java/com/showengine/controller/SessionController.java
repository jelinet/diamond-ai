package com.showengine.controller;

import com.showengine.service.SessionPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionPersistenceService sessionPersistenceService;

    @GetMapping
    public List<Map<String, Object>> list() {
        return sessionPersistenceService.listSummaries();
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return sessionPersistenceService.searchSummaries(q, limit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> get(@PathVariable String id) {
        return sessionPersistenceService.load(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> save(@PathVariable String id, @RequestBody JsonNode body) {
        try {
            sessionPersistenceService.save(id, body);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        sessionPersistenceService.delete(id);
        return ResponseEntity.ok().build();
    }
}
