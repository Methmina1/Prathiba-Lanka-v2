package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    // TODO: Add custom queries if needed
}