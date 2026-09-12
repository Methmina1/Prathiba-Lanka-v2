package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.GalleryImage;
import com.example.PrathibaLanka.entity.TravelPackage;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.GalleryImageRepository;
import com.example.PrathibaLanka.repository.TravelPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GalleryService {

    private final GalleryImageRepository galleryRepo;
    private final TravelPackageRepository packageRepo;
    private final AdminRepository adminRepo;

    /**
     * Upload/add a new gallery image (admin only).
     * - imageUrl is provided by the frontend (URL string; file upload is a separate concern).
     * - packageId is optional.
     */
    public GalleryImage uploadImage(String imageUrl, String caption, Long packageId, Long adminId) {
        Admin admin = adminRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admin not found with id: " + adminId));

        GalleryImage image = new GalleryImage();
        image.setImageUrl(imageUrl);
        image.setCaption(caption);
        image.setUploadedBy(admin);

        if (packageId != null) {
            TravelPackage pkg = packageRepo.findById(packageId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Package not found with id: " + packageId));
            image.setTravelPackage(pkg);
        }

        return galleryRepo.save(image);
    }

    /**
     * Update image URL, caption or package association of an existing image.
     * Only the fields that are provided (non-null) are changed.
     */
    public GalleryImage updateImage(Long imageId, String imageUrl, String caption, Long packageId) {
        GalleryImage image = galleryRepo.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId));

        if (imageUrl != null && !imageUrl.isBlank()) {
            image.setImageUrl(imageUrl);
        }

        if (caption != null) {
            image.setCaption(caption);
        }

        if (packageId != null) {
            TravelPackage pkg = packageRepo.findById(packageId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Package not found with id: " + packageId));
            image.setTravelPackage(pkg);
        }

        return galleryRepo.save(image);
    }

    /**
     * Delete a gallery image.
     */
    public void deleteImage(Long imageId) {
        GalleryImage image = galleryRepo.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId));
        galleryRepo.delete(image);
    }

    /**
     * Public: list all gallery images (for the Gallery page).
     */
    @Transactional(readOnly = true)
    public List<GalleryImage> getAllImages() {
        return galleryRepo.findAll();
    }

    /**
     * Public: get a single image by ID.
     */
    @Transactional(readOnly = true)
    public GalleryImage getImageById(Long imageId) {
        return galleryRepo.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId));
    }

    /**
     * Public: list images for a specific package.
     */
    @Transactional(readOnly = true)
    public List<GalleryImage> getImagesByPackage(Long packageId) {
        return galleryRepo.findByTravelPackage_PackageId(packageId);
    }
}