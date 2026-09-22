package com.example.PrathibaLanka.event;

/** Published when an admin cancels (rejects) a booking. */
public record BookingCancelledEvent(Long bookingId) {
}
