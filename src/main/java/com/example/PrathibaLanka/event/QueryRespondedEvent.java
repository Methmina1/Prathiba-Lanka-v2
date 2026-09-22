package com.example.PrathibaLanka.event;

/**
 * Published when a reply to an enquiry is stored; the customer is emailed after the commit.
 *
 * <p>Carries both ids so the mail worker can re-load the row and the message it has to send: a reply
 * that rolled back must never be mailed, and the message is what the customer actually reads.
 */
public record QueryRespondedEvent(Long queryId, Long messageId) {
}
