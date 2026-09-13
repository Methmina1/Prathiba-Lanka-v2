package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.BookingRequest;
import com.example.PrathibaLanka.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {

    Optional<BookingRequest> findByPinCode(String pinCode);

    List<BookingRequest> findByStatus(BookingStatus status);

    List<BookingRequest> findByCustomer_CustomerId(Long customerId);

    /** Travelers already committed to a package; feeds the capacity check. */
    @Query("select coalesce(sum(b.numTravelers), 0) from BookingRequest b "
            + "where b.travelPackage.packageId = :packageId and b.status in :statuses")
    long sumTravelersByStatusIn(@Param("packageId") Long packageId,
                                @Param("statuses") Collection<BookingStatus> statuses);
}
