package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.GalleryImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {
    List<GalleryImage> findByTravelPackage_PackageId(Long packageId);
}