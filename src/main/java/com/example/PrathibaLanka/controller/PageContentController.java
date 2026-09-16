package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.PageContentRequestDTO;
import com.example.PrathibaLanka.dto.response.PageContentResponseDTO;
import com.example.PrathibaLanka.entity.PageContent;
import com.example.PrathibaLanka.enums.ContentSection;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.PageContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/** The About and Contact pages: read by everyone, written by admins. */
@RestController
@RequiredArgsConstructor
public class PageContentController {

    private final PageContentService contentService;

    // ---------------- PUBLIC ----------------

    @GetMapping("/api/content/{section}")
    public ResponseEntity<PageContentResponseDTO> bySection(@PathVariable String section) {
        return ResponseEntity.ok(toDTO(contentService.getOrThrow(parseSection(section))));
    }

    // ---------------- ADMIN ----------------

    @GetMapping("/api/admin/content")
    public ResponseEntity<List<PageContentResponseDTO>> all() {
        return ResponseEntity.ok(Arrays.stream(ContentSection.values())
                .flatMap(section -> contentService.find(section).stream())
                .map(this::toDTO)
                .toList());
    }

    @PutMapping("/api/admin/content/{section}")
    public ResponseEntity<PageContentResponseDTO> save(@PathVariable String section,
                                                       @Valid @RequestBody PageContentRequestDTO dto,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        PageContent saved = contentService.save(parseSection(section), dto.getPayload(), principal.getUserId());
        return ResponseEntity.ok(toDTO(saved));
    }

    /** Accepts about/ABOUT/About so a hand-written request is not rejected on casing. */
    private ContentSection parseSection(String section) {
        try {
            return ContentSection.valueOf(section.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BadRequestException("Unknown content section '" + section + "'. Expected one of: "
                    + String.join(", ", Arrays.stream(ContentSection.values()).map(Enum::name).toList()) + ".");
        }
    }

    private PageContentResponseDTO toDTO(PageContent content) {
        PageContentResponseDTO dto = new PageContentResponseDTO();
        dto.setSection(content.getSection());
        dto.setPayload(contentService.payloadOf(content));
        dto.setUpdatedAt(content.getUpdatedAt());
        if (content.getUpdatedBy() != null) {
            dto.setUpdatedByName(content.getUpdatedBy().getFullName());
        }
        return dto;
    }
}
