package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.MessageDirection;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One message on an enquiry, as the console sees it.
 *
 * <p>{@code emailed} is only ever exposed here: to a customer it would be noise, and to staff it is the
 * difference between a reply the application sent and one somebody recorded after sending it
 * themselves.
 */
@Data
public class QueryMessageDTO {
    private Long messageId;
    private MessageDirection direction;
    private String body;
    private String authorName;
    private Boolean emailed;
    private LocalDateTime createdAt;
}
