package com.aisip.OnO.backend.mission.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE mission_log SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "mission_log", indexes = {
        @Index(name = "idx_mission_log_type_created", columnList = "mission_type, created_at"),
        @Index(name = "idx_mission_log_user_created", columnList = "user_id, created_at")
})
public class MissionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private MissionType missionType;

    private Long point;

    private Long referenceId;

    public static MissionLog from(MissionRegisterDto missionRegisterDto, User user) {
        return MissionLog.builder()
                .user(user)
                .missionType(missionRegisterDto.missionType())
                .point(missionRegisterDto.missionType().getPoint())
                .referenceId(missionRegisterDto.referenceId())
                .build();
    }
}
