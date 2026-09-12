package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.QueryRequestDTO;
import com.example.PrathibaLanka.dto.request.QueryRespondRequestDTO;
import com.example.PrathibaLanka.dto.response.QueryResponseDTO;
import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.ContactQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ContactController {

    private final ContactQueryService queryService;

    // ---------------- PUBLIC ----------------

    @PostMapping("/api/contact")
    public ResponseEntity<QueryResponseDTO> submit(@Valid @RequestBody QueryRequestDTO dto) {
        ContactQuery q = queryService.submitQuery(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(q));
    }

    // ---------------- ADMIN ----------------

    @GetMapping("/api/admin/queries")
    public ResponseEntity<List<QueryResponseDTO>> allQueries(
            @RequestParam(required = false) Boolean onlyNew) {
        List<ContactQuery> queries = Boolean.TRUE.equals(onlyNew)
                ? queryService.getNewQueries()
                : queryService.getAllQueries();
        return ResponseEntity.ok(queries.stream().map(this::toDTO).toList());
    }

    @PatchMapping("/api/admin/queries/{id}/respond")
    public ResponseEntity<QueryResponseDTO> respond(
            @PathVariable Long id,
            @Valid @RequestBody QueryRespondRequestDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        ContactQuery updated = queryService.respondToQuery(id, dto.getAdminResponse(), principal.getUserId());
        return ResponseEntity.ok(toDTO(updated));
    }

    // ---------------- MAPPER ----------------

    private QueryResponseDTO toDTO(ContactQuery q) {
        QueryResponseDTO dto = new QueryResponseDTO();
        dto.setQueryId(q.getQueryId());
        dto.setName(q.getName());
        dto.setEmail(q.getEmail());
        dto.setPhone(q.getPhone());
        dto.setSubject(q.getSubject());
        dto.setMessage(q.getMessage());
        dto.setAutoResponseSent(q.getAutoResponseSent());
        dto.setAdminResponse(q.getAdminResponse());
        dto.setRespondedByName(q.getRespondedBy() != null ? q.getRespondedBy().getFullName() : null);
        dto.setStatus(q.getStatus());
        dto.setSubmittedAt(q.getSubmittedAt());
        dto.setRespondedAt(q.getRespondedAt());
        return dto;
    }
}