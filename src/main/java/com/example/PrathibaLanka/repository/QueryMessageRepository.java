package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.QueryMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Records whether the mail worker sent this message.
     *
     * <p>A targeted update, for the same reason as {@code ContactQueryRepository.markReplySent}: the
     * worker holds the entity for as long as the mail takes, so saving it back would write every column
     * as it stood when the message was loaded - undoing anything that arrived in the meantime.
     */
    @Modifying(clearAutomatically = true)
    @Query("update QueryMessage m set m.emailed = :emailed where m.messageId = :messageId")
    int markEmailed(@Param("messageId") Long messageId, @Param("emailed") boolean emailed);
}
