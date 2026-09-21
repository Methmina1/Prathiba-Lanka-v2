package com.example.PrathibaLanka.service.mail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The request the Bird integration sends, asserted without a network or an API key.
 *
 * <p>The endpoint and the credential can only be proven by a real send, but the shape of the document
 * is the part that is easy to get subtly wrong - a recipient that is not wrapped in {@code address},
 * a {@code reply_to} at the top level instead of inside {@code content} - so it is pinned here.
 */
class BirdMailTransportTest {

    private static final OutboundMail MAIL = new OutboundMail(
            "Prathibha Lanka Voyages <bookings@mail.prathibalanka.com>",
            "traveller@example.com",
            "prathibhalankavoyages@gmail.com",
            "Your Trip Booking is Pending – PIN: ABC12345",
            "Dear Traveller,\n\nYour booking is pending.\n");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> content(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("content");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstRecipient(Map<String, Object> payload) {
        return ((List<Map<String, Object>>) payload.get("recipients")).get(0);
    }

    @Test
    void buildsATransmissionWithTheContentTheApiExpects() {
        Map<String, Object> payload = BirdMailTransport.payload(MAIL);

        assertThat(payload).containsOnlyKeys("options", "content", "recipients");

        assertThat(content(payload))
                .containsEntry("from", MAIL.from())
                .containsEntry("subject", MAIL.subject())
                .containsEntry("text", MAIL.text());
    }

    @Test
    void addressesTheRecipientTheWayTheApiWantsItWrapped() {
        // recipients[0].address.email - not recipients[0].email, which is the mistake that costs an
        // afternoon of reading a 422.
        Map<String, Object> recipient = firstRecipient(BirdMailTransport.payload(MAIL));

        assertThat(recipient).containsOnlyKeys("address");
        assertThat(recipient.get("address")).isEqualTo(Map.of("email", "traveller@example.com"));
    }

    @Test
    void marksTheMessageTransactional() {
        // These are notifications to one person, not a campaign: transactional keeps them out of the
        // bulk machinery (and out of unsubscribe requirements).
        assertThat(BirdMailTransport.payload(MAIL).get("options")).isEqualTo(Map.of("transactional", true));
    }

    @Test
    void sendsTheReplyToInsideTheContent() {
        assertThat(content(BirdMailTransport.payload(MAIL)))
                .containsEntry("reply_to", "prathibhalankavoyages@gmail.com");
    }

    @Test
    void leavesTheReplyToOutEntirelyWhenThereIsNone() {
        OutboundMail withoutReplyTo = new OutboundMail(MAIL.from(), MAIL.to(), null, MAIL.subject(), MAIL.text());

        assertThat(content(BirdMailTransport.payload(withoutReplyTo))).doesNotContainKey("reply_to");
    }

    @Test
    void treatsABlankReplyToAsNoReplyTo() {
        OutboundMail blank = new OutboundMail(MAIL.from(), MAIL.to(), "  ", MAIL.subject(), MAIL.text());

        assertThat(content(BirdMailTransport.payload(blank))).doesNotContainKey("reply_to");
    }

    @Test
    void namesTheEndpointItWillCallRatherThanTheClientObject() {
        // This string goes into the startup log, so it has to be the URL.
        BirdMailTransport transport = transport("key", "mail.prathibalanka.com", "bookings@mail.prathibalanka.com");

        assertThat(transport.describe())
                .isEqualTo("Bird API (https://api.eu.sparkpost.com/api/v1/transmissions)");
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
        return new BirdMailTransport(
                "https://api.eu.sparkpost.com/api/v1/transmissions", key, domain, from);
    }
}
