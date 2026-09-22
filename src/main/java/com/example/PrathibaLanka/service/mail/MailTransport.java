package com.example.PrathibaLanka.service.mail;

/**
 * How an email leaves the application.
 *
 * There are two ways to reach the same authenticated sending domain, and which one is usable depends
 * on where the application runs rather than on the message itself:
 *
 * <ul>
 *   <li>{@code smtp} - JavaMail to a mail server. What local development, CI (which points it at
 *       {@code scripts/fake-smtp.ps1}) and any host that permits SMTP use.</li>
 *   <li>{@code bird} - the Bird email API over HTTPS. Railway blocks outbound SMTP on its Free,
 *       Trial and Hobby plans, so on those an HTTPS API is the only transport that works at all.</li>
 * </ul>
 *
 * Which one is in use is configuration ({@code app.mail.transport}); exactly one bean of this type
 * exists, so nothing that sends mail has to know the difference.
 */
public interface MailTransport {

    /**
     * Hands one message to the provider.
     *
     * @throws Exception when the provider refused or could not be reached. The caller records the
     *                   message in {@code email_log} either way, so a failure is never silent.
     */
    void send(OutboundMail mail) throws Exception;

    /** What the startup log and a failure reason say about this transport, e.g. "SMTP" or a URL. */
    String describe();
}
