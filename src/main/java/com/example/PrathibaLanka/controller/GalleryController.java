package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.GalleryUploadRequestDTO;
import com.example.PrathibaLanka.dto.response.GalleryResponseDTO;
import com.example.PrathibaLanka.entity.GalleryImage;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.GalleryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    // ---------------- PUBLIC ----------------

    @GetMapping("/api/gallery")
    public ResponseEntity<List<GalleryResponseDTO>> all() {
        return ResponseEntity.ok(galleryService.getAllImages().stream().map(this::toDTO).toList());
    }

    @GetMapping("/api/gallery/{id}")
    public ResponseEntity<GalleryResponseDTO> byId(@PathVariable Long id) {
        return ResponseEntity.ok(toDTO(galleryService.getImageById(id)));
    }

    @GetMapping("/api/gallery/package/{packageId}")
    public ResponseEntity<List<GalleryResponseDTO>> byPackage(@PathVariable Long packageId) {
        return ResponseEntity.ok(galleryService.getImagesByPackage(packageId).stream().map(this::toDTO).toList());
    }

    // ---------------- ADMIN ----------------

    @PostMapping("/api/admin/gallery")
    public ResponseEntity<GalleryResponseDTO> upload(
            @Valid @RequestBody GalleryUploadRequestDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        GalleryImage img = galleryService.uploadImage(
                dto.getImageUrl(), dto.getCaption(), dto.getPackageId(), principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(img));
    }

    @PutMapping("/api/admin/gallery/{id}")
    public ResponseEntity<GalleryResponseDTO> update(
            @PathVariable Long id,
            @RequestBody GalleryUploadRequestDTO dto) {
        return ResponseEntity.ok(toDTO(galleryService.updateImage(
                id, dto.getImageUrl(), dto.getCaption(), dto.getPackageId())));
    }

    @DeleteMapping("/api/admin/gallery/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        galleryService.deleteImage(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- MAPPER ----------------

    private GalleryResponseDTO toDTO(GalleryImage img) {
        GalleryResponseDTO dto = new GalleryResponseDTO();
        dto.setImageId(img.getImageId());
        dto.setImageUrl(img.getImageUrl());
        dto.setCaption(img.getCaption());
        if (img.getTravelPackage() != null) {
            dto.setPackageId(img.getTravelPackage().getPackageId());
            dto.setPackageTitle(img.getTravelPackage().getTitle());
        }
        dto.setUploadedByName(img.getUploadedBy() != null ? img.getUploadedBy().getFullName() : null);
        dto.setUploadedAt(img.getUploadedAt());
        return dto;
    }
}