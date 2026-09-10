package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.enums.QueryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContactQueryRepository extends JpaRepository<ContactQuery, Long> {
    List<ContactQuery> findByStatus(QueryStatus status);
}