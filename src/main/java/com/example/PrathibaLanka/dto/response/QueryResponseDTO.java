package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.QueryStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class QueryResponseDTO {
    private Long queryId;
    private String name;
    private String email;
    private String phone;
    private String subject;
    private String message;
    private Boolean autoResponseSent;
    /**
     * The most recent reply on the thread, or null when nobody has answered.
     *
     * <p>Kept alongside {@code messages} because the console's list shows a one-line summary of where
     * each enquiry stands, and paging through a thread to find the last line is not that.
     */
    private String adminResponse;
    private String respondedByName;
    /** Whether the reply the console composed actually reached the customer. */
    private Boolean replySent;
    /** The agency answered from its own inbox instead - recorded, not a failure. */
    private Boolean answeredOutside;
    private QueryStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime respondedAt;
    /** The customer's own page, for pasting into a reply written by hand. */
    private String enquiryUrl;
    private List<QueryMessageDTO> messages;
}
