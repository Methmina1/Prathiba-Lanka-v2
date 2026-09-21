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
 * Sends through Bird's platform API over HTTPS.
 *
 * <p>Why not just SMTP: Railway blocks outbound SMTP on its Free, Trial and Hobby plans (see
 * <a href="https://docs.railway.com/networking/outbound-networking">Railway's outbound networking</a>),
 * so a provider reachable only by SMTP cannot send anything there.
 *
 * <p>Why this endpoint and not the SparkPost-compatible one: a Bird API key's region prefix selects
 * the host - {@code bk_us1_} keys call {@code https://us1.platform.bird.com} and {@code bk_eu1_} keys
 * call {@code https://eu1.platform.bird.com} - and the email endpoint there is
 * {@code POST /v1/email/messages}. The older {@code api.sparkpost.com/.../transmissions} endpoint
 * answers such a key with 401, which is a confusing way to spend an afternoon: it looks like a bad
 * key rather than the wrong host.
 *
 * <p>The request it accepts was established against the live API rather than guessed:
 *
 * <pre>
 * POST https://eu1.platform.bird.com/v1/email/messages
 * Authorization: Bearer &lt;key&gt;
 * {
 *   "from":     { "email": "bookings@mail.prathibalanka.com", "name": "Prathibha Lanka Voyages" },
 *   "to":       [ "traveller@example.com" ],
 *   "reply_to": [ "bookings@mail.prathibalanka.com" ],       // an ARRAY - a string is 422
 *   "category": "transactional",                             // without it Bird files it as marketing
 *   "subject":  "...",
 *   "text":     "..."
 * }
 * </pre>
 *
 * <p>A 202 means Bird has accepted the message; delivery happens afterwards, so a 202 is "queued",
 * not "delivered". Anything else throws with the provider's own body, which is what lands in
 * {@code email_log} - e.g. a wrong key reads {@code 401 … "code":"Unauthorized"}.
 */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "bird")
public class BirdMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(BirdMailTransport.class);

    /** Marks a message as a notification rather than a campaign. */
    static final String TRANSACTIONAL = "transactional";

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
        // fail the whole context on the one configuration that needs this class.
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
                .header("Authorization", "Bearer " + apiKey)
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
     * The request body Bird's email endpoint expects, built where a test can see it.
     *
     * <p>Package-private on purpose: the shape of this document is the part of the integration that
     * can be verified without an API key or a network, and it is the part most likely to be got
     * wrong - {@code reply_to} as a string rather than an array is a 422, and a missing
     * {@code category} silently files a booking confirmation as marketing.
     */
    static Map<String, Object> payload(OutboundMail mail) {
        Map<String, Object> from = new LinkedHashMap<>();
        from.put("email", mail.fromEmail());
        if (mail.fromName() != null && !mail.fromName().isBlank()) {
            from.put("name", mail.fromName().trim());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(mail.to()));
        if (mail.replyTo() != null && !mail.replyTo().isBlank()) {
            body.put("reply_to", List.of(mail.replyTo().trim()));
        }
        body.put("category", TRANSACTIONAL);
        body.put("subject", mail.subject());
        body.put("text", mail.text());

        return body;
    }
}
