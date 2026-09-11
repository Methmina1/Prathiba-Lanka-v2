package com.example.PrathibaLanka.entity;

import com.example.PrathibaLanka.enums.EmailType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_log")
@Data
public class EmailLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private BookingRequest bookingRequest;    // nullable

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EmailType emailType;

    @Column(nullable = false, length = 150)
    private String recipientEmail;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(nullable = false)
    private Boolean sent = false;

    @Column(length = 255)
    private String failureReason;

    @CreationTimestamp
    private LocalDateTime sentAt;
}