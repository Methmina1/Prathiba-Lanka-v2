package com.example.PrathibaLanka.event;

/** Published inside the booking transaction; the pending-mail listener reacts after the commit. */
public record BookingCreatedEvent(Long bookingId) {
}
