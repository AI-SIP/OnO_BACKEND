package com.aisip.OnO.backend.notice.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 서비스 전체에 노출하는 공지 한 건.
 *
 * <p>관리자가 제거하면 행을 지우지 않고 soft delete 로 내린다. 어떤 공지를 언제
 * 띄웠는지가 나중에 필요할 수 있어서 이력을 남긴다.
 */
@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE service_notice SET deleted_at = now(6) WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "service_notice", indexes = {
        @Index(name = "idx_service_notice_active", columnList = "expires_at, starts_at")
})
public class ServiceNotice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeType type;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public static ServiceNotice of(String title, String content, NoticeType type,
                                   LocalDateTime startsAt, LocalDateTime expiresAt) {
        return ServiceNotice.builder()
                .title(title)
                .content(content)
                .type(type)
                .startsAt(startsAt)
                .expiresAt(expiresAt)
                .build();
    }
}
