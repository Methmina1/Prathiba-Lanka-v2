package com.example.PrathibaLanka.service.mail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The request the Bird integration sends, asserted without a network or an API key.
 *
 * <p>The credential and the endpoint were established against the live API - a {@code bk_eu1_} key
 * calls {@code eu1.platform.bird.com} and not the SparkPost-compatible host, which answers it with a
 * 401 that looks like a bad key. What is pinned here is the document, because two of its details were
 * refusals from the API rather than guesses: {@code reply_to} has to be an array (a string is a 422),
 * and without {@code category} Bird files the message as marketing.
 */
class BirdMailTransportTest {

    private static final String API_URL = "https://eu1.platform.bird.com/v1/email/messages";

    private static final OutboundMail MAIL = new OutboundMail(
            "bookings@mail.prathibalanka.com",
            "Prathibha Lanka Voyages",
            "traveller@example.com",
            "bookings@mail.prathibalanka.com",
            "Your Trip Booking is Pending – PIN: ABC12345",
            "Dear Traveller,\n\nYour booking is pending.\n");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> from(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("from");
    }

    @Test
    void buildsTheDocumentTheEmailEndpointAccepts() {
        Map<String, Object> payload = BirdMailTransport.payload(MAIL);

        assertThat(payload).containsOnlyKeys("from", "to", "reply_to", "category", "subject", "text");
        assertThat(payload)
                .containsEntry("subject", MAIL.subject())
                .containsEntry("text", MAIL.text());
    }

    @Test
    void sendsTheFromAddressAsAnObjectWithItsDisplayName() {
        // Bird wants {"email": …, "name": …}, not the "Name <address>" header SMTP takes.
        assertThat(from(BirdMailTransport.payload(MAIL)))
                .containsEntry("email", "bookings@mail.prathibalanka.com")
                .containsEntry("name", "Prathibha Lanka Voyages");
    }

    @Test
    void leavesTheDisplayNameOutWhenThereIsNone() {
        OutboundMail nameless = new OutboundMail(
                MAIL.fromEmail(), null, MAIL.to(), MAIL.replyTo(), MAIL.subject(), MAIL.text());

        assertThat(from(BirdMailTransport.payload(nameless))).containsOnlyKeys("email");
    }

    @Test
    void addressesTheRecipientAsAList() {
        assertThat(BirdMailTransport.payload(MAIL).get("to")).isEqualTo(List.of("traveller@example.com"));
    }

    @Test
    void sendsTheReplyToAsAListBecauseAStringIsRefused() {
        // "got string, want array" - the API's own words, and the reason the sending domain's reply
        // address has to be wrapped.
        assertThat(BirdMailTransport.payload(MAIL).get("reply_to"))
                .isEqualTo(List.of("bookings@mail.prathibalanka.com"));
    }

    @Test
    void leavesTheReplyToOutEntirelyWhenThereIsNone() {
        OutboundMail withoutReplyTo = new OutboundMail(
                MAIL.fromEmail(), MAIL.fromName(), MAIL.to(), null, MAIL.subject(), MAIL.text());

        assertThat(BirdMailTransport.payload(withoutReplyTo)).doesNotContainKey("reply_to");
    }

    @Test
    void treatsABlankReplyToAsNoReplyTo() {
        OutboundMail blank = new OutboundMail(
                MAIL.fromEmail(), MAIL.fromName(), MAIL.to(), "  ", MAIL.subject(), MAIL.text());

        assertThat(BirdMailTransport.payload(blank)).doesNotContainKey("reply_to");
    }

    @Test
    void marksTheMessageTransactionalSoItIsNotFiledAsMarketing() {
        assertThat(BirdMailTransport.payload(MAIL)).containsEntry("category", "transactional");
    }

    @Test
    void namesTheEndpointItWillCallRatherThanTheClientObject() {
        // This string goes into the startup log, so it has to be the URL.
        assertThat(transport("key", "mail.prathibalanka.com", "bookings@mail.prathibalanka.com").describe())
                .isEqualTo("Bird API (" + API_URL + ")");
    }

    @Test
    void buildsEvenWhenTheFromAddressIsNotOnTheSendingDomain() {
        // A mismatch is a warning at startup, not a crash: the message is refused by the provider and
        // recorded in email_log, which is a better failure than a context that will not start.
        assertThat(transport("key", "mail.prathibalanka.com", "prathibhalankavoyages@gmail.com")).isNotNull();
    }

    @Test
    void refusesToPretendItSentWithoutAKey() {
        BirdMailTransport transport = transport("", "mail.prathibalanka.com", "bookings@mail.prathibalanka.com");

        assertThatThrownBy(() -> transport.send(MAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BIRD_API_KEY");
    }

    private static BirdMailTransport transport(String key, String domain, String from) {
        return new BirdMailTransport(API_URL, key, domain, from);
    }
}
