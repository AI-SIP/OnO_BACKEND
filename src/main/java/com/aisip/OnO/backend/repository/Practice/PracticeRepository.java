package com.aisip.OnO.backend.repository.Practice;

import com.aisip.OnO.backend.entity.Problem.Practice;
import com.aisip.OnO.backend.entity.Problem.Problem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PracticeRepository extends JpaRepository<Practice, Long>, PracticeRepositoryCustom {

    @EntityGraph(attributePaths = {"user"})
    List<Practice> findAllByUserId(Long userId);
}
