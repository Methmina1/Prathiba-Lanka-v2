package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.QueryStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An enquiry as its own author sees it, reached with the token from their acknowledgement email.
 *
 * <p>Deliberately smaller than the console's view: no contact details (they are the reader's own), no
 * {@code emailed} flags, no internal ids beyond the reference they were given, and nothing about who on
 * the staff side answered.
 */
@Data
public class EnquiryViewDTO {
    private Long queryId;
    private String name;
    private String subject;
    private String message;
    private QueryStatus status;
    private Boolean awaitingReply;
    private LocalDateTime submittedAt;
    private LocalDateTime respondedAt;
    private List<EnquiryMessageDTO> messages;
}
