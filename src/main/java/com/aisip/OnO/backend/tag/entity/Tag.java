package com.aisip.OnO.backend.tag.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE tag SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
// 주의: idx_tag_user_normalized 의 실제 정의는 V23 마이그레이션이 관리한다.
// soft delete 된 행이 유니크 자리를 차지해 같은 이름을 다시 만들 수 없던 문제 때문에,
// DB 에서는 (user_id, normalized_name, alive_key) 세 컬럼으로 잡혀 있다.
// alive_key 는 deleted_at 기반 생성 컬럼이라 엔티티에 매핑하지 않는다.
// ddl-auto 가 validate 라 아래 선언은 문서 역할만 한다.
@Table(name = "tag", indexes = {
        @Index(name = "idx_tag_user_id", columnList = "user_id"),
        @Index(name = "idx_tag_user_normalized", columnList = "user_id, normalized_name", unique = true)
})
public class Tag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, length = 30)
    private String normalizedName;

    public static Tag from(Long userId, String name, String normalizedName) {
        return Tag.builder()
                .userId(userId)
                .name(name)
                .normalizedName(normalizedName)
                .build();
    }
}
