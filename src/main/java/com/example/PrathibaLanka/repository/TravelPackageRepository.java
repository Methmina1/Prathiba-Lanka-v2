package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.TravelPackage;
import com.example.PrathibaLanka.enums.PackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TravelPackageRepository extends JpaRepository<TravelPackage, Long> {
    List<TravelPackage> findByStatus(PackageStatus status);
    List<TravelPackage> findByDestinationContainingIgnoreCaseAndStatus(
            String destination, PackageStatus status);
}