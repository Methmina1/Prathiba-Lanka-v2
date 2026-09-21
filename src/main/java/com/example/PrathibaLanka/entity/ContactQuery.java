package com.example.PrathibaLanka.entity;

import com.example.PrathibaLanka.enums.QueryStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "contact_query")
@Data
public class ContactQuery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long queryId;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    private Boolean autoResponseSent = false;

    /**
     * The secret that opens this enquiry's own page ({@code GET /api/enquiries/{token}}).
     *
     * <p>Random rather than the query id: that route is public, and a sequential id would let anybody
     * read anybody's enquiry by counting upwards. The id stays the human-readable reference, which is
     * what appears in subject lines and in the console.
     */
    @Column(nullable = false, length = 64, unique = true)
    private String accessToken;

    /**
     * Whether the reply composed in the console reached the customer. Written by the mail worker after
     * the response, from what the transport said - not from what the request hoped.
     */
    @Column(nullable = false)
    private Boolean replySent = false;

    /**
     * The agency answered from its own inbox instead of from here. A real outcome rather than a
     * failure, and deliberately not the same flag as a reply that failed to send.
     */
    @Column(nullable = false)
    private Boolean answeredOutside = false;

    @ManyToOne
    @JoinColumn(name = "responded_by")
    private Admin respondedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QueryStatus status = QueryStatus.NEW;

    @CreationTimestamp
    private LocalDateTime submittedAt;

    private LocalDateTime respondedAt;
}