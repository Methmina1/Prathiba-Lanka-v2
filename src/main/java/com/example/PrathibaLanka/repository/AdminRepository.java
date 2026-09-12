package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    /** Case-insensitive lookup so "Admin@X.com" and "admin@x.com" are the same account. */
    Optional<Admin> findByEmailIgnoreCase(String email);
}
