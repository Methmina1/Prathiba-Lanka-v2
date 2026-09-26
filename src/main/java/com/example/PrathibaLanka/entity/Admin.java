package com.example.PrathibaLanka.entity;

import com.example.PrathibaLanka.enums.Role;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin")
@Data
public class Admin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminId;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Role role = Role.ADMIN;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * When this password was last changed from inside the application, or null if it never has been.
     *
     * <p>Tokens carry the moment they were issued, and one issued before this is refused - which is
     * what makes "change your password" mean something for sessions that are already open. Null for
     * every admin that has never changed it, so no existing token is invalidated by this column
     * appearing.
     */
    private LocalDateTime passwordChangedAt;
}