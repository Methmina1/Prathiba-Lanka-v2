package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.response.MediaAssetResponseDTO;
import com.example.PrathibaLanka.entity.MediaAsset;
import com.example.PrathibaLanka.enums.MediaType;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Upload and manage the images and short videos the public site shows.
 *
 * <p>Uploads are multipart: the file part is {@code file} and the optional caption is {@code title}.
 * Stored files are served from {@link com.example.PrathibaLanka.config.MediaWebConfig}'s public path.
 */
@RestController
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    // ---------------- ADMIN ----------------

    @PostMapping(value = "/api/admin/media", consumes = "multipart/form-data")
    public ResponseEntity<MediaAssetResponseDTO> upload(@RequestPart("file") MultipartFile file,
                                                        @RequestParam(value = "title", required = false) String title,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        MediaAsset asset = mediaService.upload(file, title, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(asset));
    }

    @GetMapping("/api/admin/media")
    public ResponseEntity<List<MediaAssetResponseDTO>> list(
            @RequestParam(value = "type", required = false) MediaType type) {
        return ResponseEntity.ok(mediaService.list(type).stream().map(this::toDTO).toList());
    }

    /** What the console is allowed to upload, so the limits live in one place. */
    @GetMapping("/api/admin/media/limits")
    public ResponseEntity<Map<String, Object>> limits() {
        return ResponseEntity.ok(mediaService.limits());
    }

    @DeleteMapping("/api/admin/media/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- MAPPER ----------------

    private MediaAssetResponseDTO toDTO(MediaAsset asset) {
        MediaAssetResponseDTO dto = new MediaAssetResponseDTO();
        dto.setMediaId(asset.getMediaId());
        dto.setOriginalName(asset.getOriginalName());
        dto.setContentType(asset.getContentType());
        dto.setMediaType(asset.getMediaType());
        dto.setSizeBytes(asset.getSizeBytes());
        dto.setUrl(asset.getUrl());
        dto.setTitle(asset.getTitle());
        dto.setUploadedAt(asset.getUploadedAt());
        if (asset.getUploadedBy() != null) {
            dto.setUploadedByName(asset.getUploadedBy().getFullName());
        }
        return dto;
    }
}
