package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.entity.QueryMessage;
import com.example.PrathibaLanka.event.BookingCancelledEvent;
import com.example.PrathibaLanka.event.BookingConfirmedEvent;
import com.example.PrathibaLanka.event.BookingCreatedEvent;
import com.example.PrathibaLanka.event.QueryMessagePostedEvent;
import com.example.PrathibaLanka.event.QueryRespondedEvent;
import com.example.PrathibaLanka.event.QuerySubmittedEvent;
import com.example.PrathibaLanka.repository.BookingRequestRepository;
import com.example.PrathibaLanka.repository.ContactQueryRepository;
import com.example.PrathibaLanka.repository.QueryMessageRepository;
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
    private final QueryMessageRepository messageRepo;
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
    public void onBookingCancelled(BookingCancelledEvent event) {
        bookingRepo.findById(event.bookingId()).ifPresentOrElse(
                emailService::sendBookingCancelledEmail,
                () -> log.warn("Booking {} disappeared before its cancellation mail was sent", event.bookingId()));
    }

    @Async("mailExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuerySubmitted(QuerySubmittedEvent event) {
        queryRepo.findById(event.queryId()).ifPresent(query ->
                queryRepo.markAutoResponseSent(event.queryId(), emailService.sendAutoResponse(query)));
    }

    /**
     * The agency's reply, on its way to the customer.
     *
     * <p>This is the mail the console's Reply button used to only pretend to send. Both flags are
     * written from what the transport actually said: {@code replySent} on the enquiry, so the console
     * can show whether the customer was reached, and {@code emailed} on the message, so the thread
     * distinguishes a reply that was sent from one recorded after the fact.
     *
     * <p>Both are written with targeted updates rather than by saving the entities. Sending takes
     * seconds, and this runs on the mail worker: saving the enquiry afterwards would write every column
     * back as it stood when this handler loaded it, undoing a customer's follow-up that arrived while
     * the email was in flight. The enabled flag is the only thing this handler is entitled to change.
     */
    @Async("mailExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueryResponded(QueryRespondedEvent event) {
        queryRepo.findById(event.queryId()).ifPresentOrElse(query -> {
            QueryMessage reply = messageRepo.findById(event.messageId()).orElse(null);
            if (reply == null) {
                log.warn("Reply {} to enquiry {} disappeared before it was sent",
                        event.messageId(), event.queryId());
                return;
            }
            boolean sent = emailService.sendQueryReplyEmail(query, reply);
            messageRepo.markEmailed(event.messageId(), sent);
            queryRepo.markReplySent(event.queryId(), sent);
        }, () -> log.warn("Enquiry {} disappeared before its reply was sent", event.queryId()));
    }

    /**
     * "The customer wrote again" - to the inbox the agency answers from, not to the customer.
     *
     * <p>A failure here is not a failure of the enquiry: the message is on the row either way, which is
     * why nothing but the message's {@code emailed} flag records it.
     */
    @Async("mailExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueryMessagePosted(QueryMessagePostedEvent event) {
        queryRepo.findById(event.queryId()).ifPresentOrElse(query -> {
            QueryMessage message = messageRepo.findById(event.messageId()).orElse(null);
            if (message == null) {
                log.warn("Message {} on enquiry {} disappeared before the agency was told",
                        event.messageId(), event.queryId());
                return;
            }
            messageRepo.markEmailed(event.messageId(), emailService.sendQueryMessageToAgency(query, message));
        }, () -> log.warn("Enquiry {} disappeared before its new message was reported", event.queryId()));
    }
}
