package com.aisip.OnO.backend.repository;

import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Image.ImageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImageDataRepository extends JpaRepository<ImageData, Long> {
    List<ImageData> findByProblemId(Long problemId);

    Optional<ImageData> findByImageUrl(String imageUrl);
    Optional<ImageData> findByProblemIdAndImageType(Long problemId, ImageType imageType);
}
