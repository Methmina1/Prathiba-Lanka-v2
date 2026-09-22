package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.enums.QueryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ContactQueryRepository extends JpaRepository<ContactQuery, Long> {
    List<ContactQuery> findByStatus(QueryStatus status);

    /** The customer's own page is opened by the secret in their acknowledgement, not by the id. */
    Optional<ContactQuery> findByAccessToken(String accessToken);
}
