package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.MessageDirection;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One message on an enquiry, as the person who wrote in sees it.
 *
 * <p>Same shape as the console's {@link QueryMessageDTO} minus {@code emailed}: whether the application
 * or a person sent the answer is the agency's business, not something the customer needs to read.
 */
@Data
public class EnquiryMessageDTO {
    private Long messageId;
    private MessageDirection direction;
    private String body;
    private String authorName;
    private LocalDateTime createdAt;
}
