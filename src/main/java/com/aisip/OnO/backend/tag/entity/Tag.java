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
