package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.TravelPackage;
import com.example.PrathibaLanka.enums.PackageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TravelPackageRepository extends JpaRepository<TravelPackage, Long> {

    List<TravelPackage> findByStatus(PackageStatus status);

    List<TravelPackage> findByDestinationContainingIgnoreCaseAndStatus(
            String destination, PackageStatus status);

    /**
     * Reads the package while holding a row lock, so two concurrent bookings for the same package
     * cannot both pass the capacity check. The lock lives until the booking transaction commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TravelPackage p where p.packageId = :id")
    Optional<TravelPackage> findByIdForUpdate(@Param("id") Long id);
}
