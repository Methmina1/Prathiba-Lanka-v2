package com.example.PrathibaLanka.service.mail;

/**
 * One notification email, on its way out.
 *
 * Plain text only, because that is what every message this application sends is: a booking
 * acknowledgement, a confirmation and an enquiry auto-response, all of them a few lines of text with
 * a PIN in them.
 *
 * @param from    the sender the recipient sees, as {@code Name <address>}
 * @param to      the recipient
 * @param replyTo where a reply should go; may be null when the sender can receive mail itself
 */
public record OutboundMail(String from, String to, String replyTo, String subject, String text) {
}
