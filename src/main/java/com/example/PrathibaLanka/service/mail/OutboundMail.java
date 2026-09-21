package com.example.PrathibaLanka.service.mail;

/**
 * One notification email, on its way out.
 *
 * <p>Plain text only, because that is what every message this application sends is: a booking
 * acknowledgement, a confirmation and an enquiry auto-response, all of them a few lines of text with
 * a PIN in them.
 *
 * <p>The sender is two fields rather than one formatted header because the two transports want it
 * differently: SMTP takes {@code Name <address>}, and Bird's API takes {@code {"email": …, "name": …}}.
 * Formatting it here and parsing it there would be a bug waiting for a name with a bracket in it.
 *
 * @param fromEmail the address the recipient sees, and the one that has to be on the verified domain
 * @param fromName  display name; may be null or blank
 * @param to        the recipient
 * @param replyTo   where a reply should go; may be null when the sender can receive mail itself
 */
public record OutboundMail(String fromEmail, String fromName, String to, String replyTo,
                           String subject, String text) {

    /** "Name <address>", or the bare address when there is no display name. */
    public String formattedFrom() {
        return formatSender(fromEmail, fromName);
    }

    /** The same, for the places that log the sender without having a message in hand. */
    public static String formatSender(String email, String name) {
        return (name == null || name.isBlank()) ? email : name.trim() + " <" + email + ">";
    }
}
