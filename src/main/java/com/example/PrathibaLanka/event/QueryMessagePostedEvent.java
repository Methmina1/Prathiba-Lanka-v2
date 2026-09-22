package com.example.PrathibaLanka.event;

/**
 * Published when a customer writes again on their enquiry; the agency inbox is told afterwards.
 *
 * <p>The notification exists because the agency answers from that inbox: without it, a message
 * written on the website would sit in the console until somebody happened to look.
 */
public record QueryMessagePostedEvent(Long queryId, Long messageId) {
}
