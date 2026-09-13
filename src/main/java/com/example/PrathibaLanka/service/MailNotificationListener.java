package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.event.BookingConfirmedEvent;
import com.example.PrathibaLanka.event.BookingCreatedEvent;
import com.example.PrathibaLanka.event.QuerySubmittedEvent;
import com.example.PrathibaLanka.repository.BookingRequestRepository;
import com.example.PrathibaLanka.repository.ContactQueryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends notification mail outside the request: after the publishing transaction committed
 * (so a rolled-back booking never mails a PIN) and on the mail pool instead of the request thread.
 *
 * <p>Each handler runs in its own transaction and re-loads the entity, because the instance from
 * the publishing thread is detached by then.
 */
@Service
@RequiredArgsConstructor
public class MailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationListener.class);

    private final BookingRequestRepository bookingRepo;
    private final ContactQueryRepository queryRepo;
    private final EmailService emailService;

    @Async("mailExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCreated(BookingCreatedEvent event) {
        bookingRepo.findById(event.bookingId()).ifPresentOrElse(
                emailService::sendBookingPendingEmail,
                () -> log.warn("Booking {} disappeared before its pending mail was sent", event.bookingId()));
    }

    @Async("mailExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        bookingRepo.findById(event.bookingId()).ifPresentOrElse(
                emailService::sendBookingConfirmedEmail,
                () -> log.warn("Booking {} disappeared before its confirmation mail was sent", event.bookingId()));
    }

    @Async("mailExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuerySubmitted(QuerySubmittedEvent event) {
        queryRepo.findById(event.queryId()).ifPresent(query ->
                query.setAutoResponseSent(emailService.sendAutoResponse(query)));
    }
}
