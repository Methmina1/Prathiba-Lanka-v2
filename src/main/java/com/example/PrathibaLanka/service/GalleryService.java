package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.GalleryImage;
import com.example.PrathibaLanka.entity.TravelPackage;
import com.example.PrathibaLanka.enums.MediaType;
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

    public GalleryImage uploadImage(String imageUrl, String caption, Long packageId, MediaType mediaType,
                                    Long adminId) {
        Admin admin = adminRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admin not found with id: " + adminId));

        GalleryImage image = new GalleryImage();
        image.setImageUrl(imageUrl);
        image.setCaption(caption);
        image.setMediaType(mediaType == null ? MediaType.IMAGE : mediaType);
        image.setUploadedBy(admin);

        if (packageId != null) {
            TravelPackage pkg = packageRepo.findById(packageId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Package not found with id: " + packageId));
            image.setTravelPackage(pkg);
        }

        return galleryRepo.save(image);
    }

    /** Partial update: only the non-null arguments are applied. */
    public GalleryImage updateImage(Long imageId, String imageUrl, String caption, Long packageId,
                                    MediaType mediaType) {
        GalleryImage image = galleryRepo.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId));

        if (imageUrl != null && !imageUrl.isBlank()) {
            image.setImageUrl(imageUrl);
        }

        if (mediaType != null) {
            image.setMediaType(mediaType);
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

    public void deleteImage(Long imageId) {
        GalleryImage image = galleryRepo.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId));
        galleryRepo.delete(image);
    }

    @Transactional(readOnly = true)
    public List<GalleryImage> getAllImages() {
        return galleryRepo.findAll();
    }

    @Transactional(readOnly = true)
    public GalleryImage getImageById(Long imageId) {
        return galleryRepo.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId));
    }

    @Transactional(readOnly = true)
    public List<GalleryImage> getImagesByPackage(Long packageId) {
        return galleryRepo.findByTravelPackage_PackageId(packageId);
    }
}
