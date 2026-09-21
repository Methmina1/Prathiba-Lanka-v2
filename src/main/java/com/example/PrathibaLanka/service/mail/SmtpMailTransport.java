package com.example.PrathibaLanka.service.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends over SMTP, which is the transport local development and CI use.
 *
 * <p>The {@link JavaMailSender} is looked up rather than injected because it only exists when
 * {@code spring.mail.host} is set - an application configured for the Bird API has no use for one -
 * and its absence used to mean the mail was logged instead of sent, not that the context failed.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailTransport implements MailTransport {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    public void send(OutboundMail mail) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException(
                    "No JavaMailSender is configured (spring.mail.host is empty), so nothing was sent.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mail.formattedFrom());
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.text());
        if (mail.replyTo() != null && !mail.replyTo().isBlank()) {
            message.setReplyTo(mail.replyTo());
        }

        mailSender.send(message);
    }

    @Override
    public String describe() {
        return "SMTP";
    }
}
