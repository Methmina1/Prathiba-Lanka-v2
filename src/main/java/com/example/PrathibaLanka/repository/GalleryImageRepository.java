package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.GalleryImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {
    // TODO: gonna have to add the queries later if necessary
}