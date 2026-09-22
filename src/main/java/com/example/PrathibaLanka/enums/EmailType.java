package com.example.PrathibaLanka.enums;

public enum EmailType {
    AUTO_RESPONSE, PENDING_NOTIFICATION, CONFIRMATION, CANCELLATION,
    /** The agency's reply to an enquiry, sent to the customer. */
    QUERY_RESPONSE,
    /** "The customer wrote again", sent to the agency inbox that answers. */
    QUERY_MESSAGE
}
