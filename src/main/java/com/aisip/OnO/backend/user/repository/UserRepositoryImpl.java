package com.aisip.OnO.backend.user.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static com.aisip.OnO.backend.problem.entity.QProblem.problem;
import static com.aisip.OnO.backend.user.entity.QUser.user;

public class UserRepositoryImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public UserRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<UserAdminRow> findAdminUsers(Pageable pageable, String sortBy, String direction) {
        NumberExpression<Long> problemCount = problem.id.count();

        List<Tuple> rows = queryFactory
                .select(user, problemCount)
                .from(user)
                .leftJoin(problem).on(problem.userId.eq(user.id))
                .groupBy(user.id)
                .orderBy(getOrderSpecifier(sortBy, direction, problemCount), user.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<UserAdminRow> content = rows.stream()
                .map(row -> new UserAdminRow(row.get(user), row.get(problemCount)))
                .toList();

        Long total = queryFactory
                .select(user.count())
                .from(user)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private OrderSpecifier<?> getOrderSpecifier(
            String sortBy,
            String direction,
            NumberExpression<Long> problemCount
    ) {
        Order order = "asc".equalsIgnoreCase(direction) ? Order.ASC : Order.DESC;

        return switch (sortBy) {
            case "level" -> new OrderSpecifier<>(order, user.userMissionStatus.totalStudyLevel);
            case "problemCount" -> new OrderSpecifier<>(order, problemCount);
            default -> new OrderSpecifier<>(order, user.createdAt);
        };
    }
}
