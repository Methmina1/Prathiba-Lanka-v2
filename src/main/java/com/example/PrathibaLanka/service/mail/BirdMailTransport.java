package com.example.PrathibaLanka.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends through Bird's email API over HTTPS.
 *
 * <p>Why this exists rather than "just use SMTP": Railway blocks outbound SMTP on its Free, Trial and
 * Hobby plans (see <a href="https://docs.railway.com/networking/outbound-networking">Railway's
 * outbound networking</a>), so a provider reachable only by SMTP cannot send anything there. Bird -
 * which is the platform SparkPost became - sends over HTTPS, which is not blocked.
 *
 * <p>Both transports reach the same authenticated sending domain: the DKIM key, the bounce CNAME and
 * the DMARC record published for that domain apply to the API exactly as they do to SMTP, so nothing
 * about the DNS changes when the transport does.
 *
 * <p>An API key is the whole credential, and it goes in the {@code Authorization} header as sent
 * (the API does not want a {@code Bearer} prefix). The key belongs in the environment, never in this
 * repository, and is never logged - {@link #describe()} prints the endpoint and not the key.
 */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "bird")
public class BirdMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(BirdMailTransport.class);

    private final RestClient http;
    private final String apiUrl;
    private final String apiKey;

    public BirdMailTransport(@Value("${app.mail.bird.api-url}") String apiUrl,
                             @Value("${app.mail.bird.api-key:}") String apiKey,
                             @Value("${app.mail.bird.sending-domain:}") String sendingDomain,
                             @Value("${app.mail.from}") String fromAddress) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        // Built here rather than injected: this application has no RestClient.Builder bean to inject
        // (Spring Boot 4 does not publish one by default), and a missing constructor argument would
        // fail the whole context on the one configuration that needs this class. Jackson is on the
        // classpath, which is all the converters this call needs.
        this.http = RestClient.builder().baseUrl(apiUrl).build();

        if (this.apiKey.isEmpty()) {
            log.error("app.mail.transport is 'bird' but BIRD_API_KEY is not set: every send will fail "
                    + "and be recorded in email_log. Set the key, or set MAIL_TRANSPORT=smtp.");
        }

        // Bird only sends as a domain it has verified, so a From address anywhere else is refused (or,
        // worse, accepted and fails authentication at the recipient). Said at startup rather than left
        // to the first enquiry.
        if (!sendingDomain.isBlank() && !fromAddress.toLowerCase().endsWith("@" + sendingDomain.toLowerCase())) {
            log.warn("MAIL_FROM is '{}' but the Bird sending domain is '{}'. Bird will only send as an "
                            + "address on that domain - set MAIL_FROM to something@{}.",
                    fromAddress, sendingDomain, sendingDomain);
        }
    }

    @Override
    public void send(OutboundMail mail) {
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("BIRD_API_KEY is not set, so the message was not sent.");
        }

        http.post()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", apiKey)
                .body(payload(mail))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public String describe() {
        // The endpoint, not the client object: this line ends up in the startup log and in
        // email_log's failure reason, and "DefaultRestClient@4f1d0d" helps nobody.
        return "Bird API (" + apiUrl + ")";
    }

    /**
     * The request body the Transmissions API expects, built where a test can see it.
     *
     * <p>Package-private on purpose: the shape of this document is the part of the integration that
     * can be verified without an API key or a network, and it is the part most likely to be got wrong.
     */
    static Map<String, Object> payload(OutboundMail mail) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("from", mail.from());
        content.put("subject", mail.subject());
        content.put("text", mail.text());
        if (mail.replyTo() != null && !mail.replyTo().isBlank()) {
            content.put("reply_to", mail.replyTo());
        }

        // transactional: this is a notification to one person, not a campaign, so it is exempt from
        // the unsubscribe machinery and must not be throttled as bulk.
        Map<String, Object> options = Map.of("transactional", true);

        return Map.of(
                "options", options,
                "content", content,
                "recipients", List.of(Map.of("address", Map.of("email", mail.to()))));
    }
}
