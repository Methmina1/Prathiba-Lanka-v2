package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.dto.request.QueryRequestDTO;
import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.entity.Customer;
import com.example.PrathibaLanka.entity.QueryMessage;
import com.example.PrathibaLanka.enums.MessageDirection;
import com.example.PrathibaLanka.enums.QueryStatus;
import com.example.PrathibaLanka.event.QueryMessagePostedEvent;
import com.example.PrathibaLanka.event.QueryRespondedEvent;
import com.example.PrathibaLanka.event.QuerySubmittedEvent;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.ContactQueryRepository;
import com.example.PrathibaLanka.repository.CustomerRepository;
import com.example.PrathibaLanka.repository.QueryMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enquiries, and the conversation that follows them.
 *
 * <p>Two kinds arrive through the same door: a general question from the contact form, and one about a
 * specific journey, which is a booking request and has a status machine of its own. Both end up here as
 * a row plus a thread, because both need the same thing - a way for the customer and the agency to
 * write to each other that does not lose either half of the exchange.
 *
 * <p>What this service does <em>not</em> do is hold the conversation. The agency discusses the details
 * from its own inbox, which is where the reply-to on every message points. So each message here is
 * recorded rather than carried: an AGENCY message with {@code emailed = false} is somebody reporting a
 * reply they sent themselves, and it is not an error.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ContactQueryService {

    /** 128 bits of randomness, hex-encoded: the secret that opens one enquiry's own page. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ContactQueryRepository queryRepo;
    private final QueryMessageRepository messageRepo;
    private final CustomerRepository customerRepo;
    private final AdminRepository adminRepo;
    private final ApplicationEventPublisher events;

    /** Accepts guest queries too: customerId is optional. */
    public ContactQuery submitQuery(QueryRequestDTO dto) {
        ContactQuery query = new ContactQuery();

        if (dto.getCustomerId() != null) {
            Customer customer = customerRepo.findById(dto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer not found with id: " + dto.getCustomerId()));
            query.setCustomer(customer);
        }

        query.setName(dto.getName());
        query.setEmail(dto.getEmail());
        query.setPhone(dto.getPhone());
        query.setSubject(dto.getSubject());
        query.setMessage(dto.getMessage());
        query.setStatus(QueryStatus.NEW);
        query.setAutoResponseSent(false);
        query.setAccessToken(newAccessToken());

        ContactQuery saved = queryRepo.save(query);

        // The auto-response is sent after this transaction commits, on the mail pool. The returned
        // query therefore still shows autoResponseSent = false; the mail worker flips it.
        events.publishEvent(new QuerySubmittedEvent(saved.getQueryId()));

        return saved;
    }

    /**
     * The agency answers from the console, and the customer is emailed the reply.
     *
     * <p>The reply is stored first and mailed after the commit (see MailNotificationListener), which is
     * what keeps the two honest: a reply that rolled back is never sent, and a transport that is down
     * leaves the reply recorded with {@code replySent = false} instead of losing what somebody wrote.
     */
    public ContactQuery respondToQuery(Long queryId, String adminResponse, Long adminId) {
        ContactQuery query = require(queryId);

        if (query.getStatus() == QueryStatus.RESPONDED) {
            throw new BadRequestException("This query has already been responded to.");
        }

        Admin admin = requireAdmin(adminId);

        QueryMessage reply = record(query, MessageDirection.AGENCY, adminResponse,
                admin.getFullName(), admin);

        query.setStatus(QueryStatus.RESPONDED);
        query.setRespondedBy(admin);
        query.setRespondedAt(LocalDateTime.now());
        // Cleared rather than left alone: this reply has not been sent yet. The mail worker sets it from
        // what the transport says, which is also what lets a retry be told apart from a first attempt.
        query.setReplySent(false);
        query.setAnsweredOutside(false);

        ContactQuery saved = queryRepo.save(query);
        events.publishEvent(new QueryRespondedEvent(saved.getQueryId(), reply.getMessageId()));
        return saved;
    }

    /**
     * "I answered them from my inbox."
     *
     * <p>The normal way the details get agreed, and it has to be recordable without pretending a mail
     * was sent: the enquiry stops being on the waiting list, the note (if any) joins the thread, and
     * {@code answeredOutside} says why no mail went out - which is a different thing from a reply that
     * failed to send.
     */
    public ContactQuery markAnsweredOutside(Long queryId, String note, Long adminId) {
        ContactQuery query = require(queryId);

        if (query.getStatus() == QueryStatus.RESPONDED && query.getAnsweredOutside()) {
            throw new BadRequestException("This query is already marked as answered outside the system.");
        }

        Admin admin = requireAdmin(adminId);

        record(query, MessageDirection.AGENCY, note, admin.getFullName(), admin);

        query.setStatus(QueryStatus.RESPONDED);
        query.setRespondedBy(admin);
        query.setRespondedAt(LocalDateTime.now());
        query.setAnsweredOutside(true);
        query.setReplySent(false);

        return queryRepo.save(query);
    }

    /**
     * The customer writes again, from the page their own token opens.
     *
     * <p>Their message goes back into the waiting list: whoever answered last is not the person who has
     * to answer now. The previous answer stays on the thread and {@code respondedBy} stays as the record
     * of it, so nothing about the history is rewritten to make the status tidy.
     */
    public ContactQuery addCustomerMessage(String accessToken, String message) {
        ContactQuery query = findByToken(accessToken);

        QueryMessage posted = record(query, MessageDirection.CUSTOMER, message, query.getName(), null);

        if (query.getStatus() == QueryStatus.RESPONDED) {
            query.setStatus(QueryStatus.NEW);
        }

        ContactQuery saved = queryRepo.save(query);
        events.publishEvent(new QueryMessagePostedEvent(saved.getQueryId(), posted.getMessageId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public ContactQuery findByToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResourceNotFoundException("Enquiry not found.");
        }
        return queryRepo.findByAccessToken(accessToken.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry not found."));
    }

    @Transactional(readOnly = true)
    public List<ContactQuery> getAllQueries() {
        return queryRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<ContactQuery> getNewQueries() {
        return queryRepo.findByStatus(QueryStatus.NEW);
    }

    /** One enquiry's thread, oldest first. */
    @Transactional(readOnly = true)
    public List<QueryMessage> messagesOf(Long queryId) {
        return messageRepo.findByQueryQueryIdOrderByCreatedAtAsc(queryId);
    }

    /** The threads of a whole page of the console, in one query rather than one per row. */
    @Transactional(readOnly = true)
    public Map<Long, List<QueryMessage>> messagesOf(Collection<ContactQuery> queries) {
        List<Long> ids = queries.stream().map(ContactQuery::getQueryId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return messageRepo.findAllForQueries(ids).stream()
                .collect(Collectors.groupingBy(message -> message.getQuery().getQueryId()));
    }

    private ContactQuery require(Long queryId) {
        return queryRepo.findById(queryId)
                .orElseThrow(() -> new ResourceNotFoundException("Query not found with id: " + queryId));
    }

    private Admin requireAdmin(Long adminId) {
        return adminRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminId));
    }

    private QueryMessage record(ContactQuery query, MessageDirection direction, String body,
                                String authorName, Admin author) {
        QueryMessage message = new QueryMessage();
        message.setQuery(query);
        message.setDirection(direction);
        message.setBody(body == null || body.isBlank() ? null : body.trim());
        message.setAuthorName(authorName);
        // False until the mail worker says otherwise; a message recorded after the fact stays false.
        message.setEmailed(false);
        message.setAuthorAdmin(author);
        return messageRepo.save(message);
    }

    /**
     * A fresh secret for a new enquiry.
     *
     * <p>Not checked against the unique index: 128 random bits collide with probability that rounds to
     * zero, and a retry loop would only be there to look careful.
     */
    private String newAccessToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
