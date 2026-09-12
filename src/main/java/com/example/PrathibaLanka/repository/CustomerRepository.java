package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    /** Case-insensitive lookup so "User@X.com" and "user@x.com" are the same account. */
    Optional<Customer> findByEmailIgnoreCase(String email);
}
