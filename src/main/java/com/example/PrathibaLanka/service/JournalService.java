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

    private static final String PUBLISHED = "PUBLISHED";
    private static final String DRAFT = "DRAFT";

    private final JournalPostRepository journalRepo;

    public JournalPost createPost(JournalRequestDTO dto) {
        JournalPost post = new JournalPost();
        post.setTitle(dto.getTitle());
        post.setDescription(dto.getDescription());
        post.setContent(dto.getContent());
        post.setCoverImageUrl(dto.getCoverImageUrl());
        post.setStatus(dto.getStatus() != null ? normalizeStatus(dto.getStatus()) : DRAFT);

        if (PUBLISHED.equals(post.getStatus())) {
            post.publish();
        }

        return journalRepo.save(post);
    }

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
            if (PUBLISHED.equals(newStatus)) {
                if (post.getPublishedAt() == null) {
                    post.setPublishedAt(LocalDateTime.now());
                }
            } else if (DRAFT.equals(newStatus)) {
                post.setPublishedAt(null);
            }
        }

        return journalRepo.save(post);
    }

    /** Status is plain text in the schema, so keep one canonical casing. */
    private String normalizeStatus(String status) {
        return status.trim().toUpperCase();
    }

    public JournalPost publishPost(Long journalId) {
        JournalPost post = journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal post not found with id: " + journalId));
        post.publish();
        return journalRepo.save(post);
    }

    public JournalPost unpublishPost(Long journalId) {
        JournalPost post = journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal post not found with id: " + journalId));
        post.unPublish();
        return journalRepo.save(post);
    }

    public void deletePost(Long journalId) {
        JournalPost post = journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal post not found with id: " + journalId));
        journalRepo.delete(post);
    }

    @Transactional(readOnly = true)
    public JournalPost getPostById(Long journalId) {
        return journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal post not found with id: " + journalId));
    }

    @Transactional(readOnly = true)
    public List<JournalPost> getPublishedPosts() {
        return journalRepo.findByStatus(PUBLISHED);
    }

    @Transactional(readOnly = true)
    public List<JournalPost> getAllPosts() {
        return journalRepo.findAll();
    }
}
