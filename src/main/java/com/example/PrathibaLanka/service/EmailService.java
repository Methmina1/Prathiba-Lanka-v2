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
                transport.send(new OutboundMail(sender(), to, replyTo(), subject, body));
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
        return (fromName == null || fromName.isBlank())
                ? fromAddress
                : fromName.trim() + " <" + fromAddress + ">";
    }

    /** Null when no reply-to is configured, so the transport leaves the header off entirely. */
    private String replyTo() {
        return (replyToAddress == null || replyToAddress.isBlank()) ? null : replyToAddress.trim();
    }

    public boolean sendBookingPendingEmail(BookingRequest booking) {
        String to = booking.getCustomer().getEmail();
        String subject = "Your Trip Booking is Pending – PIN: " + booking.getPinCode();
        String body = "Dear " + booking.getCustomer().getFullName() + ",\n\n"
                + "Thank you for your booking request.\n"
                + "Your booking is currently pending. A consultant will contact you soon.\n"
                + "Your PIN: " + booking.getPinCode() + "\n\n"
                + "You can track your booking using this PIN on our website.\n\n"
                + "Best regards,\nPrathibaLanka Team";
        return sendAndLog(to, subject, body, EmailType.PENDING_NOTIFICATION, booking);
    }

    public boolean sendBookingConfirmedEmail(BookingRequest booking) {
        String to = booking.getCustomer().getEmail();
        String subject = "Your Trip is Confirmed! – PIN: " + booking.getPinCode();
        String body = "Dear " + booking.getCustomer().getFullName() + ",\n\n"
                + "Congratulations! Your trip has been confirmed.\n"
                + "PIN: " + booking.getPinCode() + "\n"
                + "Confirmed Date: " + booking.getConfirmedDate() + "\n"
                + "Total Price: $" + booking.getConfirmedPrice() + "\n\n"
                + "We look forward to serving you.\n\n"
                + "Best regards,\nPrathibaLanka Team";
        return sendAndLog(to, subject, body, EmailType.CONFIRMATION, booking);
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