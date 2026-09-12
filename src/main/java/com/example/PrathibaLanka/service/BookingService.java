package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.dto.request.BookingRequestDTO;
import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.BookingRequest;
import com.example.PrathibaLanka.entity.Customer;
import com.example.PrathibaLanka.entity.TravelPackage;
import com.example.PrathibaLanka.enums.BookingStatus;
import com.example.PrathibaLanka.enums.PackageStatus;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.BookingRequestRepository;
import com.example.PrathibaLanka.repository.CustomerRepository;
import com.example.PrathibaLanka.repository.TravelPackageRepository;
import com.example.PrathibaLanka.security.OwnershipGuard;
import com.example.PrathibaLanka.util.PinGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRequestRepository bookingRepo;
    private final TravelPackageRepository packageRepo;
    private final CustomerRepository customerRepo;
    private final AdminRepository adminRepo;
    private final EmailService emailService;

    /**
     * Step 1: Customer requests a booking.
     * - The acting customer is taken from the authenticated principal (never trusted from the body).
     * - Validate package exists, is ACTIVE, and has capacity.
     * - Generate a unique PIN.
     * - Save booking with status PENDING.
     * - Send pending email with PIN.
     */
    public BookingRequest requestBooking(BookingRequestDTO dto, Long authenticatedCustomerId) {
        Long customerId = OwnershipGuard.requireOwnCustomerId(dto.getCustomerId(), authenticatedCustomerId);

        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        TravelPackage pkg = packageRepo.findById(dto.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + dto.getPackageId()));

        if (pkg.getStatus() != PackageStatus.ACTIVE) {
            throw new BadRequestException("This package is not available for booking.");
        }

        if (dto.getNumTravelers() == null || dto.getNumTravelers() < 1) {
            throw new BadRequestException("Number of travelers must be at least 1.");
        }

        if (pkg.getMaxCapacity() != null && dto.getNumTravelers() > pkg.getMaxCapacity()) {
            throw new BadRequestException("Number of travelers exceeds package capacity (max: "
                    + pkg.getMaxCapacity() + ").");
        }

        // Generate unique PIN (retry if collision – extremely unlikely)
        String pin;
        do {
            pin = PinGenerator.generate();
        } while (bookingRepo.findByPinCode(pin).isPresent());

        BookingRequest booking = new BookingRequest();
        booking.setPinCode(pin);
        booking.setCustomer(customer);
        booking.setTravelPackage(pkg);
        booking.setNumTravelers(dto.getNumTravelers());
        booking.setPreferredTravelDate(dto.getPreferredTravelDate());
        booking.setSpecialRequests(dto.getSpecialRequests());
        booking.setStatus(BookingStatus.PENDING);

        BookingRequest saved = bookingRepo.save(booking);

        // Send pending email (logged even if mail not configured)
        emailService.sendBookingPendingEmail(saved);

        return saved;
    }

    /**
     * Step 2: Admin confirms a booking.
     * - Set status = CONFIRMED, confirmedPrice, confirmedDate, confirmedBy.
     * - Send confirmation email with all details.
     */
    public BookingRequest confirmBooking(Long bookingId, BigDecimal confirmedPrice,
                                         LocalDate confirmedDate, Long adminId) {
        BookingRequest booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new BadRequestException("This booking is already confirmed.");
        }
        if (booking.getStatus() == BookingStatus.REJECTED) {
            throw new BadRequestException("Cannot confirm a rejected booking.");
        }

        Admin admin = adminRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminId));

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedPrice(confirmedPrice);
        booking.setConfirmedDate(confirmedDate);
        booking.setConfirmedBy(admin);

        BookingRequest saved = bookingRepo.save(booking);

        emailService.sendBookingConfirmedEmail(saved);

        return saved;
    }

    /**
     * Step 3: Admin rejects a booking.
     */
    public BookingRequest rejectBooking(Long bookingId) {
        BookingRequest booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BadRequestException("Only pending bookings can be rejected.");
        }

        booking.setStatus(BookingStatus.REJECTED);
        return bookingRepo.save(booking);
    }

    /**
     * Step 4: Public tracking – customer uses PIN to view status.
     */
    @Transactional(readOnly = true)
    public BookingRequest trackBooking(String pinCode) {
        return bookingRepo.findByPinCode(pinCode)
                .orElseThrow(() -> new ResourceNotFoundException("No booking found for PIN: " + pinCode));
    }

    /**
     * Utility: list all bookings (for admin).
     */
    @Transactional(readOnly = true)
    public List<BookingRequest> getAllBookings() {
        return bookingRepo.findAll();
    }

    /**
     * Utility: list the bookings of one customer (own-bookings endpoint).
     * Filtering happens in the database instead of loading the whole table into memory.
     */
    @Transactional(readOnly = true)
    public List<BookingRequest> getBookingsForCustomer(Long customerId) {
        return bookingRepo.findByCustomer_CustomerId(customerId);
    }

    /**
     * Utility: list bookings by status (for admin).
     */
    @Transactional(readOnly = true)
    public List<BookingRequest> getBookingsByStatus(BookingStatus status) {
        return bookingRepo.findByStatus(status);
    }
}