package com.example.PrathibaLanka.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigTest {

    private final BeanPostProcessor normaliser = MailConfig.appPasswordNormaliser();

    private JavaMailSenderImpl senderWith(String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setPassword(password);
        return (JavaMailSenderImpl) normaliser.postProcessAfterInitialization(sender, "mailSender");
    }

    @Test
    void compactsAnAppPasswordPastedWithItsDisplaySpaces() {
        // the shape Google shows: four groups of four. Deliberately not a credential-looking value.
        assertThat(senderWith("wxyz wxyz wxyz wxyz").getPassword()).isEqualTo("wxyzwxyzwxyzwxyz");
    }

    @Test
    void leavesAnUnspacedPasswordAlone() {
        assertThat(senderWith("wxyzwxyzwxyzwxyz").getPassword()).isEqualTo("wxyzwxyzwxyzwxyz");
    }

    @Test
    void leavesAnOrdinaryPasswordWithASpaceAlone() {
        assertThat(senderWith("correct horse battery staple").getPassword()).isEqualTo("correct horse battery staple");
    }

    @Test
    void toleratesAnEmptyOrMissingPassword() {
        assertThat(senderWith("").getPassword()).isEmpty();
        assertThat(senderWith(null).getPassword()).isNull();
    }

    @Test
    void leavesOtherBeansAlone() {
        Object other = new Object();
        assertThat(normaliser.postProcessAfterInitialization(other, "somethingElse")).isSameAs(other);
    }
}
