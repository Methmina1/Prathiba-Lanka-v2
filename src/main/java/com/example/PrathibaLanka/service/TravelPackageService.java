package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.dto.request.PackageRequestDTO;
import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.TravelPackage;
import com.example.PrathibaLanka.enums.PackageStatus;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.TravelPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TravelPackageService {

    private final TravelPackageRepository packageRepo;
    private final AdminRepository adminRepo;

    public TravelPackage createPackage(PackageRequestDTO dto, Long adminId) {
        Admin admin = adminRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admin not found with id: " + adminId));

        TravelPackage pkg = new TravelPackage();
        pkg.setTitle(dto.getTitle());
        pkg.setDescription(dto.getDescription());
        pkg.setDestination(dto.getDestination());
        pkg.setDurationDays(dto.getDurationDays());
        pkg.setPrice(dto.getPrice());
        pkg.setMaxCapacity(dto.getMaxCapacity());
        pkg.setItinerary(dto.getItinerary());
        pkg.setImageUrl(blankToNull(dto.getImageUrl()));
        pkg.setStatus(dto.getStatus() != null ? dto.getStatus() : PackageStatus.ACTIVE);
        pkg.setCreatedBy(admin.getAdminId());

        return packageRepo.save(pkg);
    }

    public TravelPackage updatePackage(Long packageId, PackageRequestDTO dto) {
        TravelPackage pkg = packageRepo.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Package not found with id: " + packageId));

        pkg.setTitle(dto.getTitle());
        pkg.setDescription(dto.getDescription());
        pkg.setDestination(dto.getDestination());
        pkg.setDurationDays(dto.getDurationDays());
        pkg.setPrice(dto.getPrice());
        pkg.setMaxCapacity(dto.getMaxCapacity());
        pkg.setItinerary(dto.getItinerary());
        if (dto.getImageUrl() != null) {
            // An empty string clears the image, so the drawn scene comes back.
            pkg.setImageUrl(blankToNull(dto.getImageUrl()));
        }
        if (dto.getStatus() != null) {
            pkg.setStatus(dto.getStatus());
        }

        return packageRepo.save(pkg);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Hard delete: fails with 409 while bookings still reference the package. */
    public void deletePackage(Long packageId) {
        TravelPackage pkg = packageRepo.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Package not found with id: " + packageId));
        packageRepo.delete(pkg);
    }

    /** Soft delete, safe when the package already has bookings. */
    public TravelPackage deactivatePackage(Long packageId) {
        TravelPackage pkg = packageRepo.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Package not found with id: " + packageId));
        pkg.setStatus(PackageStatus.INACTIVE);
        return packageRepo.save(pkg);
    }

    @Transactional(readOnly = true)
    public TravelPackage getPackageById(Long packageId) {
        return packageRepo.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Package not found with id: " + packageId));
    }

    @Transactional(readOnly = true)
    public List<TravelPackage> getActivePackages() {
        return packageRepo.findByStatus(PackageStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<TravelPackage> getAllPackages() {
        return packageRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<TravelPackage> searchByDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            throw new BadRequestException("Destination must not be empty.");
        }
        return packageRepo.findByDestinationContainingIgnoreCaseAndStatus(destination, PackageStatus.ACTIVE);
    }
}
