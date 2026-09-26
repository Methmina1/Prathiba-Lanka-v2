package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.entity.BookingRequest;
import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.entity.EmailLog;
import com.example.PrathibaLanka.entity.QueryMessage;
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

    /**
     * The public site, used to build the links that go in an email.
     *
     * <p>It cannot be derived from the request: mail is composed on a worker thread after the response
     * has gone, and the API's own origin is not where the customer reads their enquiry. Set
     * APP_PUBLIC_URL to the deployed front end.
     */
    @Value("${app.public-url:http://localhost:5173}")
    private String publicUrl;

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
        log.info("Links in outgoing mail point at {}", publicUrl);
    }

    /**
     * Sends the message and always writes an {@link EmailLog} row, so failed deliveries are
     * recorded instead of silently disappearing.
     *
     * @return true only if the mail server accepted the message
     */
    private boolean sendAndLog(String to, String subject, String body, EmailType type, BookingRequest booking) {
        return sendAndLog(to, subject, body, type, booking, replyTo());
    }

    /**
     * The same, with an explicit reply-to.
     *
     * <p>The notification that tells the agency "the customer wrote again" is sent <em>to</em> the
     * agency, so it must not carry the agency's own address as its reply-to: whoever answers it would
     * be writing to themselves. That one is addressed back to the customer, which is what lets the
     * person in Gmail hit reply and reach the person who wrote in.
     */
    private boolean sendAndLog(String to, String subject, String body, EmailType type, BookingRequest booking,
                               String replyToHeader) {
        boolean sent = false;
        String failureReason = null;

        MailTransport transport = transportProvider.getIfAvailable();

        if (transport != null) {
            try {
                transport.send(new OutboundMail(fromAddress, fromName, to, replyToHeader, subject, body));
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
                + "Best regards,\nPrathibhaLanka Team";
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
                + "Best regards,\nPrathibhaLanka Team";
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
                + "Best regards,\nPrathibhaLanka Team";
        return sendAndLog(to, subject, body, EmailType.CANCELLATION, booking);
    }

    public boolean sendAutoResponse(ContactQuery query) {
        String to = query.getEmail();
        // The reference in the subject, not only in the body: it is what lets whoever reads the inbox
        // match a thread to the row in the console a week later, and it is how the two "Re:" mails that
        // follow are recognised as belonging to the same enquiry.
        String subject = "[" + reference(query) + "] We received your message – PrathibhaLanka";
        String body = "Dear " + query.getName() + ",\n\n"
                + "Thank you for contacting us.\n"
                + "We have received your message and will get back to you as soon as possible.\n\n"
                + "Your enquiry reference: " + reference(query) + "\n"
                + enquiryLinkNote(query, "You can read our answer, and write back to us, at any time:")
                + "Best regards,\nPrathibhaLanka Team";
        return sendAndLog(to, subject, body, EmailType.AUTO_RESPONSE, null);
    }

    /**
     * The one-time code that lets an admin who has forgotten their password back in.
     *
     * <p>The code is in the body and not the subject, unlike the booking PIN. A subject line shows up
     * on a lock screen, on a watch, and in the list view of an inbox somebody else may be standing
     * behind; a code that resets the console password is not worth that convenience, and the person
     * waiting for it is already looking at the message.
     *
     * <p>It also says what to do if it was not them, because asking for a code changes nothing on its
     * own: the password still works, and the code expires in ten minutes unused.
     */
    public boolean sendPasswordResetCode(String to, String name, String code, int validForMinutes) {
        String subject = "Your PrathibhaLanka password reset code";
        String body = "Hello " + name + ",\n\n"
                + "Somebody asked to change the password on the admin account for " + to + ".\n\n"
                + "Your code: " + code + "\n"
                + "It works once, for the next " + validForMinutes + " minutes.\n\n"
                + "Enter it on the sign-in page, under \"Forgotten your password?\".\n\n"
                + "If this was not you, nothing has changed and you can ignore this message - your "
                + "password still works exactly as it did.\n\n"
                + "Best regards,\nPrathibhaLanka Team";
        return sendAndLog(to, subject, body, EmailType.PASSWORD_RESET, null);
    }

    /**
     * The agency's reply to an enquiry, sent to the customer.
     *
     * <p>This is the message that used to be written into the database and never sent. The subject
     * keeps the reference and the original subject so the customer's mail client threads it, the
     * customer's own message is quoted underneath so they do not have to remember what they asked, and
     * the link back to their page is what lets them answer without an account.
     */
    public boolean sendQueryReplyEmail(ContactQuery query, QueryMessage reply) {
        String to = query.getEmail();
        String subject = "Re: [" + reference(query) + "] " + query.getSubject();
        String body = "Dear " + query.getName() + ",\n\n"
                + answerOf(reply) + "\n\n"
                + "---\n"
                + "Your message, for reference:\n"
                + quote(query.getMessage()) + "\n"
                + "Your enquiry reference: " + reference(query) + "\n"
                + enquiryLinkNote(query, "You can read this answer on our site, and write back to us, at:")
                + "Best regards,\nPrathibhaLanka Team";
        return sendAndLog(to, subject, body, EmailType.QUERY_RESPONSE, null);
    }

    /**
     * "The customer wrote again", sent to the inbox the agency answers from.
     *
     * <p>Its reply-to is the customer, not the agency: the point of this mail is that whoever reads it
     * in Gmail can hit reply and reach the person who wrote in, without opening the console first. The
     * message is on the enquiry either way, so nothing is lost if it is filtered as noise.
     *
     * @return false when no agency address is configured, in which case nothing is sent or logged -
     *         email_log.recipient_email cannot hold a blank, and the message is recorded regardless
     */
    public boolean sendQueryMessageToAgency(ContactQuery query, QueryMessage message) {
        String to = replyTo();
        if (to == null) {
            log.warn("No agency inbox is configured (app.mail.reply-to), so the message on enquiry {} "
                    + "was recorded without a notification.", query.getQueryId());
            return false;
        }

        String subject = "[" + reference(query) + "] " + query.getName() + " wrote again: " + query.getSubject();
        String body = query.getName() + " (" + query.getEmail() + ") added a message to their enquiry.\n\n"
                + answerOf(message) + "\n\n"
                + "---\n"
                + "The enquiry, as it stands:\n"
                + "From: " + query.getName() + " <" + query.getEmail() + ">"
                + (query.getPhone() == null || query.getPhone().isBlank() ? "" : "  " + query.getPhone()) + "\n"
                + "Reference: " + reference(query) + "\n"
                + "Received: " + query.getSubmittedAt() + "\n\n"
                + quote(query.getMessage()) + "\n"
                + "Reply to this email to answer " + query.getName() + " directly, or record it in the "
                + "console at " + consoleLink() + " so the enquiry reads complete.\n";
        return sendAndLog(to, subject, body, EmailType.QUERY_MESSAGE, null, query.getEmail());
    }

    /** "#44" - the reference staff and customers quote at each other. */
    private String reference(ContactQuery query) {
        return "#" + query.getQueryId();
    }

    /** The customer's own page, when the enquiry has a token to open it with. */
    private String enquiryLink(ContactQuery query) {
        if (query.getAccessToken() == null || query.getAccessToken().isBlank()) {
            return null;
        }
        String base = (publicUrl == null || publicUrl.isBlank()) ? "" : publicUrl.trim().replaceAll("/+$", "");
        return base + "/enquiry/" + query.getAccessToken();
    }

    /** The "you can read it here" line, or nothing at all when there is no link to offer. */
    private String enquiryLinkNote(ContactQuery query, String lead) {
        String link = enquiryLink(query);
        return link == null ? "" : lead + "\n" + link + "\n\n";
    }

    private String consoleLink() {
        String base = (publicUrl == null || publicUrl.isBlank()) ? "" : publicUrl.trim().replaceAll("/+$", "");
        return base + "/admin/queries";
    }

    /** A recorded reply with no copy kept still has to read as a sentence. */
    private String answerOf(QueryMessage message) {
        if (message == null || message.getBody() == null || message.getBody().isBlank()) {
            return "(No copy of this reply was kept.)";
        }
        return message.getBody().trim();
    }

    /** Quotes a message back with "> " on every line, so nested quoting stays readable. */
    private String quote(String text) {
        if (text == null || text.isBlank()) {
            return "> (nothing kept)\n";
        }
        StringBuilder quoted = new StringBuilder();
        for (String line : text.trim().split("\\R")) {
            quoted.append("> ").append(line).append('\n');
        }
        return quoted.toString();
    }
}