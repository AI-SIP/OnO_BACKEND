package com.aisip.OnO.backend.notice.repository;

import com.aisip.OnO.backend.notice.entity.ServiceNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ServiceNoticeRepository extends JpaRepository<ServiceNotice, Long> {

    /**
     * 지금 노출해야 하는 공지를 최신순으로 가져온다.
     *
     * <p>활성 공지는 한 건만 두기로 했지만 등록이 겹치면 잠깐 둘이 될 수 있어서
     * 단건이 아니라 목록으로 받는다. 조회하는 쪽에서 맨 앞을 쓴다.
     *
     * <p>{@code deleted_at IS NULL} 은 엔티티의 {@code @SQLRestriction} 이 붙여 준다.
     */
    @Query("SELECT n FROM ServiceNotice n "
            + "WHERE n.startsAt <= :now AND n.expiresAt > :now "
            + "ORDER BY n.id DESC")
    List<ServiceNotice> findActiveNotices(@Param("now") LocalDateTime now);
}
