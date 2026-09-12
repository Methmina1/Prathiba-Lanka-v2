package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.dto.request.JournalRequestDTO;
import com.example.PrathibaLanka.entity.JournalPost;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.JournalPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class JournalService {

    private final JournalPostRepository journalRepo;

    /**
     * Create a new journal post (admin only).
     * Defaults to DRAFT status.
     */
    public JournalPost createPost(JournalRequestDTO dto) {
        JournalPost post = new JournalPost();
        post.setTitle(dto.getTitle());
        post.setDescription(dto.getDescription());
        post.setContent(dto.getContent());
        post.setCoverImageUrl(dto.getCoverImageUrl());

        // Default to DRAFT unless explicitly provided
        post.setStatus(dto.getStatus() != null ? normalizeStatus(dto.getStatus()) : "DRAFT");

        if ("PUBLISHED".equals(post.getStatus())) {
            post.publish();
        }

        return journalRepo.save(post);
    }

    /**
     * Update an existing journal post.
     */
    public JournalPost updatePost(Long journalId, JournalRequestDTO dto) {
        JournalPost post = journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal post not found with id: " + journalId));

        post.setTitle(dto.getTitle());
        post.setDescription(dto.getDescription());
        post.setContent(dto.getContent());
        post.setCoverImageUrl(dto.getCoverImageUrl());

        if (dto.getStatus() != null) {
            String newStatus = normalizeStatus(dto.getStatus());
            post.setStatus(newStatus);
            if ("PUBLISHED".equals(newStatus)) {
                if (post.getPublishedAt() == null) {
                    post.setPublishedAt(LocalDateTime.now());
                }
            } else if ("DRAFT".equals(newStatus)) {
                // Moving back to draft: the post is no longer published.
                post.setPublishedAt(null);
            }
        }

        return journalRepo.save(post);
    }

    /** Journal status is free text in the schema; keep a single canonical casing. */
    private String normalizeStatus(String status) {
        return status.trim().toUpperCase();
    }

    /**
     * Publish a post (sets status=PUBLISHED and publishedAt=now).
     */
    public JournalPost publishPost(Long journalId) {
        JournalPost post = journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal post not found with id: " + journalId));
        post.publish();
        return journalRepo.save(post);
    }

    /**
     * Unpublish a post (sets status=DRAFT).
     */
    public JournalPost unpublishPost(Long journalId) {
        JournalPost post = journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal post not found with id: " + journalId));
        post.unPublish();
        return journalRepo.save(post);
    }

    /**
     * Delete a journal post.
     */
    public void deletePost(Long journalId) {
        JournalPost post = journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal post not found with id: " + journalId));
        journalRepo.delete(post);
    }

    /**
     * Fetch a single post by ID.
     */
    @Transactional(readOnly = true)
    public JournalPost getPostById(Long journalId) {
        return journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal post not found with id: " + journalId));
    }

    /**
     * Public: list only PUBLISHED journal posts (for the Journal page).
     */
    @Transactional(readOnly = true)
    public List<JournalPost> getPublishedPosts() {
        return journalRepo.findByStatus("PUBLISHED");
    }

    /**
     * Admin: list all journal posts (drafts + published).
     */
    @Transactional(readOnly = true)
    public List<JournalPost> getAllPosts() {
        return journalRepo.findAll();
    }
}