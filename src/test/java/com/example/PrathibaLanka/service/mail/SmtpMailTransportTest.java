package com.example.PrathibaLanka.service.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the SMTP transport puts on the message, including the reply-to the sending domain needs. */
class SmtpMailTransportTest {

    private static final OutboundMail MAIL = new OutboundMail(
            "PrathibaLanka <bookings@mail.prathibalanka.com>",
            "traveller@example.com",
            "prathibhalankavoyages@gmail.com",
            "We received your message – PrathibaLanka",
            "Thank you for contacting us.");

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> providerReturning(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private static SimpleMailMessage sent(OutboundMail mail) {
        JavaMailSender sender = mock(JavaMailSender.class);
        new SmtpMailTransport(providerReturning(sender)).send(mail);

        ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        return captured.getValue();
    }

    @Test
    void sendsFromToAndSubjectAsConfigured() {
        SimpleMailMessage message = sent(MAIL);

        assertThat(message.getFrom()).isEqualTo(MAIL.from());
        // getTo() is the array accessor and getReplyTo() the single-value one - the opposite way round
        // from what you would guess, and both assert differently as a result.
        assertThat(message.getTo()).containsExactly(MAIL.to());
        assertThat(message.getSubject()).isEqualTo(MAIL.subject());
        assertThat(message.getText()).isEqualTo(MAIL.text());
    }

    @Test
    void setsTheReplyToWhenThereIsOne() {
        assertThat(sent(MAIL).getReplyTo()).isEqualTo("prathibhalankavoyages@gmail.com");
    }

    @Test
    void leavesTheReplyToOffWhenThereIsNone() {
        OutboundMail withoutReplyTo = new OutboundMail(MAIL.from(), MAIL.to(), null, MAIL.subject(), MAIL.text());

        assertThat(sent(withoutReplyTo).getReplyTo()).isNull();
    }

    @Test
    void explainsItselfWhenThereIsNoMailServerAtAll() {
        // spring.mail.host empty: the context has no JavaMailSender, and the message should say why
        // rather than fail with a null pointer.
        SmtpMailTransport transport = new SmtpMailTransport(providerReturning(null));

        assertThatThrownBy(() -> transport.send(MAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host");
    }

    @Test
    void namesItselfForTheStartupLog() {
        assertThat(new SmtpMailTransport(providerReturning(mock(JavaMailSender.class))).describe())
                .isEqualTo("SMTP");
    }
}
