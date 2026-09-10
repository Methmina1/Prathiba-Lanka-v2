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

    @Column(columnDefinition = "TEXT")
    private String adminResponse;

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