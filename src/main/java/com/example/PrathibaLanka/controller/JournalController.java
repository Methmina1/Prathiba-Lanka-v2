package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.JournalRequestDTO;
import com.example.PrathibaLanka.dto.response.JournalResponseDTO;
import com.example.PrathibaLanka.entity.JournalPost;
import com.example.PrathibaLanka.service.JournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class JournalController {

    private final JournalService journalService;

    // ---------------- PUBLIC ----------------

    @GetMapping("/api/journal/published")
    public ResponseEntity<List<JournalResponseDTO>> published() {
        return ResponseEntity.ok(journalService.getPublishedPosts().stream().map(this::toDTO).toList());
    }

    @GetMapping("/api/journal/published/{id}")
    public ResponseEntity<JournalResponseDTO> publishedById(@PathVariable Long id) {
        JournalPost post = journalService.getPostById(id);
        if (!"PUBLISHED".equalsIgnoreCase(post.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDTO(post));
    }

    // ---------------- ADMIN ----------------

    @GetMapping("/api/admin/journal")
    public ResponseEntity<List<JournalResponseDTO>> all() {
        return ResponseEntity.ok(journalService.getAllPosts().stream().map(this::toDTO).toList());
    }

    @GetMapping("/api/admin/journal/{id}")
    public ResponseEntity<JournalResponseDTO> byId(@PathVariable Long id) {
        return ResponseEntity.ok(toDTO(journalService.getPostById(id)));
    }

    @PostMapping("/api/admin/journal")
    public ResponseEntity<JournalResponseDTO> create(@Valid @RequestBody JournalRequestDTO dto) {
        JournalPost post = journalService.createPost(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(post));
    }

    @PutMapping("/api/admin/journal/{id}")
    public ResponseEntity<JournalResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody JournalRequestDTO dto) {
        return ResponseEntity.ok(toDTO(journalService.updatePost(id, dto)));
    }

    @PatchMapping("/api/admin/journal/{id}/publish")
    public ResponseEntity<JournalResponseDTO> publish(@PathVariable Long id) {
        return ResponseEntity.ok(toDTO(journalService.publishPost(id)));
    }

    @PatchMapping("/api/admin/journal/{id}/unpublish")
    public ResponseEntity<JournalResponseDTO> unpublish(@PathVariable Long id) {
        return ResponseEntity.ok(toDTO(journalService.unpublishPost(id)));
    }

    @DeleteMapping("/api/admin/journal/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        journalService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- MAPPER ----------------

    private JournalResponseDTO toDTO(JournalPost p) {
        JournalResponseDTO dto = new JournalResponseDTO();
        dto.setJournalId(p.getJournalId());
        dto.setTitle(p.getTitle());
        dto.setDescription(p.getDescription());
        dto.setContent(p.getContent());
        dto.setCoverImageUrl(p.getCoverImageUrl());
        dto.setStatus(p.getStatus());
        dto.setPublishedAt(p.getPublishedAt());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }
}