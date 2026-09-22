package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.dto.request.BookingRequestDTO;
import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.BookingRequest;
import com.example.PrathibaLanka.entity.Customer;
import com.example.PrathibaLanka.entity.TravelPackage;
import com.example.PrathibaLanka.enums.BookingStatus;
import com.example.PrathibaLanka.enums.PackageStatus;
import com.example.PrathibaLanka.event.BookingCancelledEvent;
import com.example.PrathibaLanka.event.BookingConfirmedEvent;
import com.example.PrathibaLanka.event.BookingCreatedEvent;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.ForbiddenException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.BookingRequestRepository;
import com.example.PrathibaLanka.repository.CustomerRepository;
import com.example.PrathibaLanka.repository.TravelPackageRepository;
import com.example.PrathibaLanka.security.OwnershipGuard;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.util.PinGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    /** Requests hold capacity until they are rejected, so the capacity check sees them. */
    private static final List<BookingStatus> OUTSTANDING_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final BookingRequestRepository bookingRepo;
    private final TravelPackageRepository packageRepo;
    private final CustomerRepository customerRepo;
    private final AdminRepository adminRepo;
    private final ApplicationEventPublisher events;

    /**
     * Creates a PENDING booking, for a signed-in customer or for somebody who has no account.
     *
     * <p>Most requests come from the public form, filled in by a traveller browsing the site, so the
     * only identity the request has is the name and email typed into it. That is enough: the booking
     * carries those details itself (see {@code contact_name}), the customer is left null, and the
     * PIN is what the person tracks it with. Nothing is invented on their behalf - creating a
     * customer row here would take their email address and stop them registering with it later.
     *
     * <p>Signed-in customers are linked to their account, and their account details win over whatever
     * the body says, so a booking cannot be made to look like it came from somebody else. If a request
     * arrives without a session but with an email that does belong to an account, it is attached to
     * that account as well - so "I booked without signing in, where is it?" has an answer.
     *
     * <p>The package row is locked for the duration of the transaction, which serializes the capacity
     * check and the insert so concurrent requests cannot oversubscribe a package.
     */
    public BookingRequest requestBooking(BookingRequestDTO dto, UserPrincipal principal) {
        Customer customer;
        String contactName;
        String contactEmail;

        if (principal != null) {
            Long customerId = OwnershipGuard.requireOwnCustomerId(dto.getCustomerId(), principal.getUserId());
            customer = customerRepo.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
            contactName = customer.getFullName();
            contactEmail = customer.getEmail();
        } else {
            if (dto.getCustomerId() != null) {
                throw new ForbiddenException(
                        "Sign in as a customer to request a journey on an account.");
            }
            contactName = trimmed(dto.getContactName());
            contactEmail = trimmed(dto.getContactEmail());
            if (contactName == null || contactEmail == null) {
                throw new BadRequestException(
                        "A name and an email address are required so we can reply to your request.");
            }
            customer = customerRepo.findByEmailIgnoreCase(contactEmail).orElse(null);
        }

        TravelPackage pkg = packageRepo.findByIdForUpdate(dto.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + dto.getPackageId()));

        if (pkg.getStatus() != PackageStatus.ACTIVE) {
            throw new BadRequestException("This package is not available for booking.");
        }

        if (dto.getNumTravelers() == null || dto.getNumTravelers() < 1) {
            throw new BadRequestException("Number of travelers must be at least 1.");
        }

        if (pkg.getMaxCapacity() != null) {
            long outstanding = bookingRepo.sumTravelersByStatusIn(pkg.getPackageId(), OUTSTANDING_STATUSES);
            if (outstanding + dto.getNumTravelers() > pkg.getMaxCapacity()) {
                throw new BadRequestException("Number of travelers exceeds the remaining capacity of this package"
                        + " (max: " + pkg.getMaxCapacity() + ", already requested: " + outstanding + ").");
            }
        }

        String pin;
        do {
            pin = PinGenerator.generate();
        } while (bookingRepo.findByPinCode(pin).isPresent());

        BookingRequest booking = new BookingRequest();
        booking.setPinCode(pin);
        booking.setCustomer(customer);
        booking.setContactName(contactName);
        booking.setContactEmail(contactEmail);
        booking.setTravelPackage(pkg);
        booking.setNumTravelers(dto.getNumTravelers());
        booking.setPreferredTravelDate(dto.getPreferredTravelDate());
        booking.setSpecialRequests(dto.getSpecialRequests());
        booking.setStatus(BookingStatus.PENDING);

        BookingRequest saved = bookingRepo.save(booking);

        events.publishEvent(new BookingCreatedEvent(saved.getBookingId()));

        return saved;
    }

    /** Trimmed text, or null when there was nothing but whitespace. */
    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

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

        events.publishEvent(new BookingConfirmedEvent(saved.getBookingId()));

        return saved;
    }

    /**
     * Cancels a pending request.
     *
     * <p>The traveller is told: both outcomes of a review are emails, not just the happy one, so a
     * request cannot sit unanswered while the person waits for news.
     */
    public BookingRequest rejectBooking(Long bookingId) {
        BookingRequest booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BadRequestException("Only pending bookings can be rejected.");
        }

        booking.setStatus(BookingStatus.REJECTED);
        BookingRequest saved = bookingRepo.save(booking);

        events.publishEvent(new BookingCancelledEvent(saved.getBookingId()));

        return saved;
    }

    @Transactional(readOnly = true)
    public BookingRequest trackBooking(String pinCode) {
        return bookingRepo.findByPinCode(pinCode)
                .orElseThrow(() -> new ResourceNotFoundException("No booking found for PIN: " + pinCode));
    }

    @Transactional(readOnly = true)
    public List<BookingRequest> getAllBookings() {
        return bookingRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<BookingRequest> getBookingsForCustomer(Long customerId) {
        return bookingRepo.findByCustomer_CustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<BookingRequest> getBookingsByStatus(BookingStatus status) {
        return bookingRepo.findByStatus(status);
    }
}
