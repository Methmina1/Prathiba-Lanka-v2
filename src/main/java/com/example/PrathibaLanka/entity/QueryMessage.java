package com.example.PrathibaLanka.entity;

import com.example.PrathibaLanka.enums.MessageDirection;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One message on an enquiry: what the customer wrote, or what the agency answered.
 *
 * <p>This is the record of the conversation, not the transport for it. The agency discusses the
 * details with people from its own inbox, so an AGENCY message with {@code emailed = false} is the
 * normal case - somebody wrote from Gmail and (if they kept a copy) pasted it here - and
 * {@code emailed = true} means this application sent it. Both are worth having: the first is how the
 * details actually get agreed, the second is how the answer reaches a customer who would otherwise
 * never hear back.
 *
 * <p>The enquiry's own message is not duplicated here: it is {@code contact_query.message}, and the
 * thread is everything that was said after it.
 */
@Entity
@Table(name = "query_message")
@Data
public class QueryMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "query_id", nullable = false)
    private ContactQuery query;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageDirection direction;

    /** Null when a reply was recorded without keeping a copy of what was sent. */
    @Column(columnDefinition = "TEXT")
    private String body;

    /** The customer's name, or the full name of the admin who wrote or recorded it. */
    @Column(length = 100)
    private String authorName;

    /** True only when this application sent it. See the class comment. */
    @Column(nullable = false)
    private Boolean emailed = false;

    /** The staff account behind an AGENCY message; null for a customer's. */
    @ManyToOne
    @JoinColumn(name = "author_admin_id")
    private Admin authorAdmin;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
