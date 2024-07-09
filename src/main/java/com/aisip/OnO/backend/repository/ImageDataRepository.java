package com.aisip.OnO.backend.repository;

import com.aisip.OnO.backend.entity.Image.ImageData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageDataRepository extends JpaRepository<ImageData, Long> {
    List<ImageData> findByProblemId(Long problemId);
}
