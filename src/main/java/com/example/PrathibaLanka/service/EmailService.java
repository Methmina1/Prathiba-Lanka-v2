package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.entity.BookingRequest;
import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.entity.EmailLog;
import com.example.PrathibaLanka.enums.EmailType;
import com.example.PrathibaLanka.repository.EmailLogRepository;
import com.example.PrathibaLanka.service.mail.MailTransport;
import com.example.PrathibaLanka.service.mail.OutboundMail;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Whichever transport the configuration chose - SMTP locally, the Bird API where SMTP is blocked.
     * It is looked up rather than injected because a configuration can deliberately have none
     * ({@code app.mail.transport=none}), and "no transport" means the messages are logged and
     * recorded rather than that the application cannot start.
     */
    private final ObjectProvider<MailTransport> transportProvider;
    private final EmailLogRepository emailLogRepository;

    /** Sender the recipient sees. Configured rather than left to JavaMail's invented default. */
    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name:}")
    private String fromName;

    /**
     * Where a reply should go.
     *
     * This matters more than it looks: the application sends from a subdomain that is set up to send
     * only (a DKIM key and a bounce record, no MX), so a customer hitting reply on a booking email
     * would otherwise write into a mailbox that does not exist. With this set, the reply lands in the
     * agency's real inbox.
     */
    @Value("${app.mail.reply-to:}")
    private String replyToAddress;

    /** One line at startup saying how mail leaves this instance, so a deployment is not a guess. */
    @PostConstruct
    void announceTransport() {
        MailTransport transport = transportProvider.getIfAvailable();
        if (transport == null) {
            log.warn("No mail transport is configured (app.mail.transport=none). Messages are written "
                    + "to email_log and not sent.");
        } else {
            log.info("Mail goes out over {} as {}", transport.describe(), sender());
        }
        if (replyToAddress != null && !replyToAddress.isBlank()) {
            log.info("Replies are directed to {}", replyToAddress);
        }
    }

    /**
     * Sends the message and always writes an {@link EmailLog} row, so failed deliveries are
     * recorded instead of silently disappearing.
     *
     * @return true only if the mail server accepted the message
     */
    private boolean sendAndLog(String to, String subject, String body, EmailType type, BookingRequest booking) {
        boolean sent = false;
        String failureReason = null;

        MailTransport transport = transportProvider.getIfAvailable();

        if (transport != null) {
            try {
                transport.send(new OutboundMail(fromAddress, fromName, to, replyTo(), subject, body));
                sent = true;
                log.info("Email sent to {} | Subject: {}", to, subject);
            } catch (Exception e) {
                failureReason = e.getMessage();
                log.error("Failed to send email to {}: {}", to, failureReason);
            }
        } else {
            failureReason = "No mail transport is configured";
            log.warn("Mail not configured. Logging only → {} | Subject: {}", to, subject);
        }

        EmailLog emailLog = new EmailLog();
        emailLog.setBookingRequest(booking);
        emailLog.setEmailType(type);
        emailLog.setRecipientEmail(to);
        emailLog.setSubject(subject);
        emailLog.setBody(body);
        emailLog.setSent(sent);
        emailLog.setFailureReason(failureReason);
        emailLog.setSentAt(LocalDateTime.now());
        emailLogRepository.save(emailLog);

        return sent;
    }

    /** "Name <address>", or the bare address when no display name is configured. */
    private String sender() {
        return OutboundMail.formatSender(fromAddress, fromName);
    }

    /** Null when no reply-to is configured, so the transport leaves the header off entirely. */
    private String replyTo() {
        return (replyToAddress == null || replyToAddress.isBlank()) ? null : replyToAddress.trim();
    }

    /**
     * Sent when a journey is requested, from the public form or from an account.
     *
     * <p>Goes to the booking's own contact address - the one typed into the form - because most
     * requests come from people who never signed in. The PIN is in the subject as well as the body:
     * it is the only thing they need afterwards, and a subject line is what they will find again.
     */
    public boolean sendBookingPendingEmail(BookingRequest booking) {
        String to = booking.getContactEmail();
        String journey = booking.getTravelPackage().getTitle();
        String subject = "Your request is pending – " + journey + " – PIN: " + booking.getPinCode();
        String body = "Dear " + booking.getContactName() + ",\n\n"
                + "Thank you for your enquiry about " + journey + ".\n\n"
                + "Your request is pending. One of our consultants will contact you shortly to go "
                + "through the dates, the hotels and the price.\n\n"
                + "Your tracking PIN: " + booking.getPinCode() + "\n"
                + "Keep it: you can follow the status of this request at any time on our website, using "
                + "the PIN tracker, without an account.\n\n"
                + "Best regards,\nPrathibaLanka Team";
        return sendAndLog(to, subject, body, EmailType.PENDING_NOTIFICATION, booking);
    }

    public boolean sendBookingConfirmedEmail(BookingRequest booking) {
        String to = booking.getContactEmail();
        String journey = booking.getTravelPackage().getTitle();
        String subject = "Your journey is confirmed – " + journey + " – PIN: " + booking.getPinCode();
        String body = "Dear " + booking.getContactName() + ",\n\n"
                + "Good news - your journey is confirmed.\n\n"
                + "Journey: " + journey + "\n"
                + "PIN: " + booking.getPinCode() + "\n"
                + "Confirmed date: " + booking.getConfirmedDate() + "\n"
                + "Agreed price: $" + booking.getConfirmedPrice() + "\n\n"
                + "We will be in touch with the documents and the meeting arrangements.\n\n"
                + "Best regards,\nPrathibaLanka Team";
        return sendAndLog(to, subject, body, EmailType.CONFIRMATION, booking);
    }

    /**
     * Sent when an admin cancels a request.
     *
     * <p>Deliberately not called "cancelled" in the subject: from the traveller's side what happened
     * is that the request could not be confirmed, and the reason is almost always capacity - the
     * dates are taken, or the journey is full. It says nothing has been charged (there is no payment
     * step anywhere in this application, and a reader cannot be expected to know that) and leaves a
     * door open, because a cancelled request is usually a date problem rather than a no.
     */
    public boolean sendBookingCancelledEmail(BookingRequest booking) {
        String to = booking.getContactEmail();
        String journey = booking.getTravelPackage().getTitle();
        String subject = "We could not confirm your request – " + journey + " – PIN: " + booking.getPinCode();
        String body = "Dear " + booking.getContactName() + ",\n\n"
                + "Thank you for your enquiry about " + journey + ".\n\n"
                + "We are sorry to say we cannot confirm this request as it stands. That usually means "
                + "the dates are already taken, or the journey is full for that period.\n\n"
                + "Nothing has been charged, and the request is now closed. If your dates can move, or "
                + "you would like us to suggest something similar, reply to this email - or send a new "
                + "request from the website - and we will find something that works.\n\n"
                + "Your reference PIN: " + booking.getPinCode() + "\n\n"
                + "Best regards,\nPrathibaLanka Team";
        return sendAndLog(to, subject, body, EmailType.CANCELLATION, booking);
    }

    public boolean sendAutoResponse(ContactQuery query) {
        String to = query.getEmail();
        String subject = "We received your message – PrathibaLanka";
        String body = "Dear " + query.getName() + ",\n\n"
                + "Thank you for contacting us.\n"
                + "We have received your message and will get back to you as soon as possible.\n\n"
                + "Your query reference: " + query.getQueryId() + "\n\n"
                + "Best regards,\nPrathibaLanka Team";
        return sendAndLog(to, subject, body, EmailType.AUTO_RESPONSE, null);
    }
}