package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.enums.QueryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContactQueryRepository extends JpaRepository<ContactQuery, Long> {
    List<ContactQuery> findByStatus(QueryStatus status);

    /** The customer's own page is opened by the secret in their acknowledgement, not by the id. */
    Optional<ContactQuery> findByAccessToken(String accessToken);

    /**
     * Marks the acknowledgement as sent, in an update of its own.
     *
     * <p>Written as a targeted update rather than by saving the entity, because this runs on the mail
     * worker while somebody may be writing back on the same enquiry. Saving a whole entity there writes
     * back every column as it stood when the worker loaded it, which quietly undoes whatever arrived
     * while the email was in flight - a customer's follow-up leaves the enquiry looking answered
     * instead of waiting again. That is not hypothetical: the live role suite caught exactly that.
     */
    @Modifying(clearAutomatically = true)
    @Query("update ContactQuery q set q.autoResponseSent = :sent where q.queryId = :queryId")
    int markAutoResponseSent(@Param("queryId") Long queryId, @Param("sent") boolean sent);

    /** The same, for the reply the console composed. */
    @Modifying(clearAutomatically = true)
    @Query("update ContactQuery q set q.replySent = :sent where q.queryId = :queryId")
    int markReplySent(@Param("queryId") Long queryId, @Param("sent") boolean sent);
}
