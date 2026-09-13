package com.example.PrathibaLanka.entity;

import com.example.PrathibaLanka.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_request")
@Data
public class BookingRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @Column(unique = true, nullable = false, length = 10)
    private String pinCode;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "package_id", nullable = false)
    private TravelPackage travelPackage;

    @Column(nullable = false)
    private Integer numTravelers;

    @Column(name = "preferred_travel_date", nullable = false)
    private LocalDate preferredTravelDate;

    @Column(columnDefinition = "TEXT")
    private String specialRequests;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(precision = 10, scale = 2)
    private BigDecimal confirmedPrice;

    private LocalDate confirmedDate;

    @ManyToOne
    @JoinColumn(name = "confirmed_by")
    private Admin confirmedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Optimistic lock: concurrent updates of the same booking (e.g. two admins confirming it)
     * conflict instead of overwriting each other. The default keeps existing rows usable when
     * ddl-auto adds the column.
     */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;
}