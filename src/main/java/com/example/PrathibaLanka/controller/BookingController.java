package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.BookingConfirmRequestDTO;
import com.example.PrathibaLanka.dto.request.BookingRequestDTO;
import com.example.PrathibaLanka.dto.response.BookingResponseDTO;
import com.example.PrathibaLanka.entity.BookingRequest;
import com.example.PrathibaLanka.enums.BookingStatus;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // ---------------- PUBLIC / CUSTOMER ----------------

    /**
     * Requests a journey.
     *
     * <p>This is the one write a visitor can make without an account, because it is the button on a
     * journey card: the person clicking it is a traveller with an email address, not a registered
     * customer. They get a PIN back and an email saying the request is pending.
     *
     * <p>Staff are excluded rather than merely not offered it - an administrator is not a customer,
     * and a booking they made would sit in the console queue they themselves work. A signed-in
     * customer's request is attached to their account; see {@link BookingService#requestBooking}.
     */
    @PostMapping("/api/bookings/request")
    @PreAuthorize("!hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDTO> requestBooking(@Valid @RequestBody BookingRequestDTO dto,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        BookingRequest booking = bookingService.requestBooking(dto, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(booking));
    }

    /** Public: tracked with the PIN, no token required. */
    @GetMapping("/api/bookings/track")
    public ResponseEntity<BookingResponseDTO> track(@RequestParam String pin) {
        return ResponseEntity.ok(toDTO(bookingService.trackBooking(pin)));
    }

    @GetMapping("/api/customer/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<BookingResponseDTO>> myBookings(@AuthenticationPrincipal UserPrincipal principal) {
        List<BookingResponseDTO> list = bookingService.getBookingsForCustomer(principal.getUserId())
                .stream().map(this::toDTO).toList();
        return ResponseEntity.ok(list);
    }

    // ---------------- ADMIN ----------------

    @GetMapping("/api/admin/bookings")
    public ResponseEntity<List<BookingResponseDTO>> allBookings(
            @RequestParam(required = false) BookingStatus status) {
        List<BookingRequest> bookings = (status == null)
                ? bookingService.getAllBookings()
                : bookingService.getBookingsByStatus(status);
        return ResponseEntity.ok(bookings.stream().map(this::toDTO).toList());
    }

    @PatchMapping("/api/admin/bookings/{id}/confirm")
    public ResponseEntity<BookingResponseDTO> confirm(
            @PathVariable Long id,
            @Valid @RequestBody BookingConfirmRequestDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        BookingRequest updated = bookingService.confirmBooking(
                id, dto.getConfirmedPrice(), dto.getConfirmedDate(), principal.getUserId());
        return ResponseEntity.ok(toDTO(updated));
    }

    @PatchMapping("/api/admin/bookings/{id}/reject")
    public ResponseEntity<BookingResponseDTO> reject(@PathVariable Long id) {
        return ResponseEntity.ok(toDTO(bookingService.rejectBooking(id)));
    }

    // ---------------- MAPPER ----------------

    private BookingResponseDTO toDTO(BookingRequest b) {
        BookingResponseDTO dto = new BookingResponseDTO();
        dto.setBookingId(b.getBookingId());
        dto.setPinCode(b.getPinCode());
        dto.setStatus(b.getStatus());
        // The booking's own contact details, not the account's: a request from the public form has no
        // account, and these are what the console replies to. For a signed-in customer they were
        // copied from the account when the request was made.
        dto.setCustomerName(b.getContactName());
        dto.setCustomerEmail(b.getContactEmail());
        dto.setPackageTitle(b.getTravelPackage().getTitle());
        dto.setDestination(b.getTravelPackage().getDestination());
        dto.setNumTravelers(b.getNumTravelers());
        dto.setPreferredTravelDate(b.getPreferredTravelDate());
        dto.setSpecialRequests(b.getSpecialRequests());
        dto.setConfirmedPrice(b.getConfirmedPrice());
        dto.setConfirmedDate(b.getConfirmedDate());
        return dto;
    }
}