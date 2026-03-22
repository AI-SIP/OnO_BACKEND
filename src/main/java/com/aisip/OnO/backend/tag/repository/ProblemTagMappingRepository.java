package com.aisip.OnO.backend.tag.repository;

import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemTagMappingRepository extends JpaRepository<ProblemTagMapping, Long> {

    List<ProblemTagMapping> findAllByProblemId(Long problemId);

    List<ProblemTagMapping> findAllByTagId(Long tagId);
}
