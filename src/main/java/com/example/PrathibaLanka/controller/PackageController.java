package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.PackageRequestDTO;
import com.example.PrathibaLanka.dto.response.PackageResponseDTO;
import com.example.PrathibaLanka.entity.TravelPackage;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.TravelPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PackageController {

    private final TravelPackageService packageService;

    // ---------------- PUBLIC ----------------

    @GetMapping("/api/packages")
    public ResponseEntity<List<PackageResponseDTO>> getActivePackages() {
        return ResponseEntity.ok(packageService.getActivePackages().stream().map(this::toDTO).toList());
    }

    @GetMapping("/api/packages/{id}")
    public ResponseEntity<PackageResponseDTO> getPackage(@PathVariable Long id) {
        return ResponseEntity.ok(toDTO(packageService.getPackageById(id)));
    }

    @GetMapping("/api/packages/search")
    public ResponseEntity<List<PackageResponseDTO>> search(@RequestParam String destination) {
        return ResponseEntity.ok(packageService.searchByDestination(destination).stream().map(this::toDTO).toList());
    }

    // ---------------- ADMIN ----------------

    @GetMapping("/api/admin/packages")
    public ResponseEntity<List<PackageResponseDTO>> getAllPackages() {
        return ResponseEntity.ok(packageService.getAllPackages().stream().map(this::toDTO).toList());
    }

    @PostMapping("/api/admin/packages")
    public ResponseEntity<PackageResponseDTO> createPackage(
            @Valid @RequestBody PackageRequestDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        TravelPackage created = packageService.createPackage(dto, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created));
    }

    @PutMapping("/api/admin/packages/{id}")
    public ResponseEntity<PackageResponseDTO> updatePackage(
            @PathVariable Long id,
            @Valid @RequestBody PackageRequestDTO dto) {
        return ResponseEntity.ok(toDTO(packageService.updatePackage(id, dto)));
    }

    @PatchMapping("/api/admin/packages/{id}/deactivate")
    public ResponseEntity<PackageResponseDTO> deactivatePackage(@PathVariable Long id) {
        return ResponseEntity.ok(toDTO(packageService.deactivatePackage(id)));
    }

    @DeleteMapping("/api/admin/packages/{id}")
    public ResponseEntity<Void> deletePackage(@PathVariable Long id) {
        packageService.deletePackage(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- MAPPER ----------------

    private PackageResponseDTO toDTO(TravelPackage pkg) {
        PackageResponseDTO dto = new PackageResponseDTO();
        dto.setPackageId(pkg.getPackageId());
        dto.setTitle(pkg.getTitle());
        dto.setDescription(pkg.getDescription());
        dto.setDestination(pkg.getDestination());
        dto.setDurationDays(pkg.getDurationDays());
        dto.setPrice(pkg.getPrice());
        dto.setMaxCapacity(pkg.getMaxCapacity());
        dto.setItinerary(pkg.getItinerary());
        dto.setStatus(pkg.getStatus());
        dto.setCreatedAt(pkg.getCreatedAt());
        dto.setUpdatedAt(pkg.getUpdatedAt());
        return dto;
    }
}