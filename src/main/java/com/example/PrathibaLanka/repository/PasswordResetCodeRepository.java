package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /** The code a reset attempt is checked against: the newest one still waiting to be used. */
    Optional<PasswordResetCode> findFirstByAdminIdAndUsedAtIsNullOrderByRequestedAtDesc(Long adminId);

    /**
     * Invalidates every code this admin has outstanding, used when a new one is requested.
     *
     * <p>Otherwise asking for a code twice would leave two live codes, and the earlier one - the one
     * most likely to have been shoulder-surfed from an inbox - would keep working for its full ten
     * minutes.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetCode c set c.usedAt = current_timestamp "
            + "where c.adminId = :adminId and c.usedAt is null")
    int discardOutstanding(@Param("adminId") Long adminId);
}
