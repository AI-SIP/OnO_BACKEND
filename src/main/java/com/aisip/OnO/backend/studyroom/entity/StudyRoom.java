package com.aisip.OnO.backend.studyroom.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "study_room", indexes = {
        @Index(name = "idx_study_room_host_user", columnList = "host_user_id")
})
public class StudyRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "host_user_id", nullable = false)
    private Long hostUserId;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StudyRoomMember> members = new ArrayList<>();

    public static StudyRoom create(String name, Long hostUserId) {
        return StudyRoom.builder()
                .name(name)
                .hostUserId(hostUserId)
                .members(new ArrayList<>())
                .build();
    }

    public void addMember(StudyRoomMember member) {
        members.add(member);
        member.updateRoom(this);
    }

    public void updateHostUserId(Long hostUserId) {
        this.hostUserId = hostUserId;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
}
