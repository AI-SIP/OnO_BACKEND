package com.aisip.OnO.backend.repository.Practice;

import com.aisip.OnO.backend.entity.Problem.*;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.repository.query.Param;

import java.util.List;

public class PracticeRepositoryImpl implements PracticeRepositoryCustom{

    private final JPAQueryFactory queryFactory;

    public PracticeRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<Practice> findAllByProblemsContaining(Problem problem) {
        QPractice practice = QPractice.practice;
        QProblemPracticeMapping practiceProblemMapping = QProblemPracticeMapping.problemPracticeMapping;

        return queryFactory
                .select(practice)
                .from(practice)
                .join(practice.problemPracticeMappings, practiceProblemMapping) // 중간 매핑 테이블 조인
                .where(practiceProblemMapping.problem.eq(problem))
                .fetch();
    }

    @Override
    public List<Problem> findAllProblemsByPracticeId(Long practiceId) {
        QPractice practice = QPractice.practice;
        QProblemPracticeMapping practiceProblemMapping = QProblemPracticeMapping.problemPracticeMapping;
        QProblem problem = QProblem.problem;

        return queryFactory
                .select(problem)
                .from(problem)
                .join(practiceProblemMapping).on(problem.id.eq(practiceProblemMapping.problem.id))
                .join(practiceProblemMapping.practice, practice)
                .where(practice.id.eq(practiceId))
                .fetch();
    }

    @Override
    public int countProblemsByPracticeId(Long practiceId) {
        QPractice practice = QPractice.practice;
        QProblemPracticeMapping practiceProblemMapping = QProblemPracticeMapping.problemPracticeMapping;

        return Math.toIntExact(queryFactory
                .select(practiceProblemMapping.count()) // 문제 개수 카운트
                .from(practiceProblemMapping)
                .where(practiceProblemMapping.practice.id.eq(practiceId))
                .fetchOne());
    }
}
