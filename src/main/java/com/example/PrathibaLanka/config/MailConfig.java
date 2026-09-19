package com.example.PrathibaLanka.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Google displays an App Password as four groups of four characters - {@code abcd efgh ijkl mnop} -
 * and people paste it with the spaces in. SMTP wants the sixteen characters, so a spaced value is
 * compacted before it reaches the server.
 *
 * <p>Only a value that is exactly sixteen characters once the whitespace is removed is touched, so an
 * ordinary password that happens to contain a space is left alone.
 */
@Configuration
public class MailConfig {

    /** Static so the post-processor is registered before the mail sender is created. */
    @Bean
    static BeanPostProcessor appPasswordNormaliser() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof JavaMailSenderImpl sender) {
                    String password = sender.getPassword();
                    if (password != null && password.indexOf(' ') >= 0) {
                        String compact = password.replaceAll("\\s", "");
                        if (compact.length() == 16) {
                            sender.setPassword(compact);
                        }
                    }
                }
                return bean;
            }
        };
    }
}
