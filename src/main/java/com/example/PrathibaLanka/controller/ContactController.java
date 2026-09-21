package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.EnquiryMessageRequestDTO;
import com.example.PrathibaLanka.dto.request.QueryAnsweredOutsideRequestDTO;
import com.example.PrathibaLanka.dto.request.QueryRequestDTO;
import com.example.PrathibaLanka.dto.request.QueryRespondRequestDTO;
import com.example.PrathibaLanka.dto.response.EnquiryMessageDTO;
import com.example.PrathibaLanka.dto.response.EnquiryViewDTO;
import com.example.PrathibaLanka.dto.response.QueryMessageDTO;
import com.example.PrathibaLanka.dto.response.QueryResponseDTO;
import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.entity.QueryMessage;
import com.example.PrathibaLanka.enums.MessageDirection;
import com.example.PrathibaLanka.enums.QueryStatus;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.ContactQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ContactController {

    private final ContactQueryService queryService;

    // ---------------- PUBLIC ----------------

    @PostMapping("/api/contact")
    public ResponseEntity<QueryResponseDTO> submit(@Valid @RequestBody QueryRequestDTO dto) {
        ContactQuery q = queryService.submitQuery(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(q, List.of()));
    }

    /**
     * The customer's own enquiry, opened by the token from their acknowledgement email.
     *
     * <p>No account and no login: the token is 128 random bits, which is the same trade the booking PIN
     * makes, and it is the only thing standing between a stranger and this page.
     */
    @GetMapping("/api/enquiries/{token}")
    public ResponseEntity<EnquiryViewDTO> enquiry(@PathVariable String token) {
        ContactQuery query = queryService.findByToken(token);
        return ResponseEntity.ok(toEnquiryView(query, queryService.messagesOf(query.getQueryId())));
    }

    /** The customer writes again. Their message goes back into the console's waiting list. */
    @PostMapping("/api/enquiries/{token}/messages")
    public ResponseEntity<EnquiryViewDTO> addMessage(
            @PathVariable String token,
            @Valid @RequestBody EnquiryMessageRequestDTO dto) {
        ContactQuery query = queryService.addCustomerMessage(token, dto.getMessage());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toEnquiryView(query, queryService.messagesOf(query.getQueryId())));
    }

    // ---------------- ADMIN ----------------

    @GetMapping("/api/admin/queries")
    public ResponseEntity<List<QueryResponseDTO>> allQueries(
            @RequestParam(required = false) Boolean onlyNew) {
        List<ContactQuery> queries = Boolean.TRUE.equals(onlyNew)
                ? queryService.getNewQueries()
                : queryService.getAllQueries();

        // One query for every thread on the page, rather than one per enquiry.
        Map<Long, List<QueryMessage>> threads = queryService.messagesOf(queries);

        return ResponseEntity.ok(queries.stream()
                .map(query -> toDTO(query, threads.getOrDefault(query.getQueryId(), List.of())))
                .toList());
    }

    /** The reply the customer actually receives: composed here, mailed after the commit. */
    @PatchMapping("/api/admin/queries/{id}/respond")
    public ResponseEntity<QueryResponseDTO> respond(
            @PathVariable Long id,
            @Valid @RequestBody QueryRespondRequestDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        ContactQuery updated = queryService.respondToQuery(id, dto.getAdminResponse(), principal.getUserId());
        return ResponseEntity.ok(toDTO(updated, queryService.messagesOf(id)));
    }

    /** "I answered them from my inbox" - recorded as answered, with nothing sent. */
    @PostMapping("/api/admin/queries/{id}/answered-outside")
    public ResponseEntity<QueryResponseDTO> answeredOutside(
            @PathVariable Long id,
            @Valid @RequestBody QueryAnsweredOutsideRequestDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        ContactQuery updated = queryService.markAnsweredOutside(id, dto.getNote(), principal.getUserId());
        return ResponseEntity.ok(toDTO(updated, queryService.messagesOf(id)));
    }

    // ---------------- MAPPERS ----------------

    private QueryResponseDTO toDTO(ContactQuery q, List<QueryMessage> messages) {
        QueryResponseDTO dto = new QueryResponseDTO();
        dto.setQueryId(q.getQueryId());
        dto.setName(q.getName());
        dto.setEmail(q.getEmail());
        dto.setPhone(q.getPhone());
        dto.setSubject(q.getSubject());
        dto.setMessage(q.getMessage());
        dto.setAutoResponseSent(q.getAutoResponseSent());
        dto.setAdminResponse(latestReply(messages));
        dto.setRespondedByName(q.getRespondedBy() != null ? q.getRespondedBy().getFullName() : null);
        dto.setReplySent(q.getReplySent());
        dto.setAnsweredOutside(q.getAnsweredOutside());
        dto.setStatus(q.getStatus());
        dto.setSubmittedAt(q.getSubmittedAt());
        dto.setRespondedAt(q.getRespondedAt());
        dto.setEnquiryUrl(q.getAccessToken() == null ? null : "/enquiry/" + q.getAccessToken());
        dto.setMessages(messages.stream().map(this::toMessageDTO).toList());
        return dto;
    }

    private QueryMessageDTO toMessageDTO(QueryMessage message) {
        QueryMessageDTO dto = new QueryMessageDTO();
        dto.setMessageId(message.getMessageId());
        dto.setDirection(message.getDirection());
        dto.setBody(message.getBody());
        dto.setAuthorName(message.getAuthorName());
        dto.setEmailed(message.getEmailed());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    private EnquiryViewDTO toEnquiryView(ContactQuery q, List<QueryMessage> messages) {
        EnquiryViewDTO dto = new EnquiryViewDTO();
        dto.setQueryId(q.getQueryId());
        dto.setName(q.getName());
        dto.setSubject(q.getSubject());
        dto.setMessage(q.getMessage());
        dto.setStatus(q.getStatus());
        dto.setAwaitingReply(q.getStatus() == QueryStatus.NEW);
        dto.setSubmittedAt(q.getSubmittedAt());
        dto.setRespondedAt(q.getRespondedAt());
        dto.setMessages(messages.stream().map(this::toEnquiryMessageDTO).toList());
        return dto;
    }

    private EnquiryMessageDTO toEnquiryMessageDTO(QueryMessage message) {
        EnquiryMessageDTO dto = new EnquiryMessageDTO();
        dto.setMessageId(message.getMessageId());
        dto.setDirection(message.getDirection());
        dto.setBody(message.getBody());
        dto.setAuthorName(message.getAuthorName());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    /** The last thing the agency said, which is what the console's list shows as the reply. */
    private String latestReply(List<QueryMessage> messages) {
        return messages.stream()
                .filter(message -> message.getDirection() == MessageDirection.AGENCY)
                .reduce((first, second) -> second)
                .map(QueryMessage::getBody)
                .orElse(null);
    }
}
