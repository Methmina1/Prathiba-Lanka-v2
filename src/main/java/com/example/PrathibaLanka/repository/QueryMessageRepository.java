package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.QueryMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QueryMessageRepository extends JpaRepository<QueryMessage, Long> {

    List<QueryMessage> findByQueryQueryIdOrderByCreatedAtAsc(Long queryId);

    Optional<QueryMessage> findByMessageIdAndQueryQueryId(Long messageId, Long queryId);

    /**
     * Every message belonging to any of these enquiries, oldest first.
     *
     * <p>One query for a whole page of the console, rather than one per row: the list is small but it
     * is still an N+1 waiting to happen, and the console loads it on every filter change.
     */
    @Query("select m from QueryMessage m where m.query.queryId in :queryIds order by m.createdAt asc, m.messageId asc")
    List<QueryMessage> findAllForQueries(@Param("queryIds") Collection<Long> queryIds);
}
