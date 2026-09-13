package com.example.PrathibaLanka.event;

/** Published when a contact query is stored; the auto-response is sent afterwards. */
public record QuerySubmittedEvent(Long queryId) {
}
