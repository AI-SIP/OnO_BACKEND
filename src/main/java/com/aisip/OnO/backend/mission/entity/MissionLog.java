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

    /**
     * 이 기록으로 자동 적립 경로가 열렸는가. 열렸으면 그 행동의 정가, 아니면 0 이다.
     *
     * <p>예전에는 지급 여부와 무관하게 언제나 정가가 들어갔다. 미션을 받을 수 있는 앱에서 온 요청은
     * 자동 적립을 돌리지 않는데도 정가가 들어가는 바람에, 하루 200점 상한({@code getPointSumToday})이
     * 받지도 않은 점수로 채워져 같은 계정의 구버전 기기 적립까지 막았다(#318).
     *
     * <p>상한 계산은 이 값의 합만 본다. 그래서 <b>실제로 적립이 돈 행만 상한을 갉는다.</b>
     * 적립이 돈 행에는 상한에 걸려 깎이기 전 정가를 그대로 넣는다. 깎인 실지급액을 넣으면
     * 상한 계산이 달라져 구버전만 쓰는 사용자의 동작이 바뀐다.
     *
     * <p>관리자 복습 로그 화면이 보여 주는 점수는 이 값이 아니라 미션 타입의 정가다
     * ({@code AdminPracticeLogResponseDto}). 화면의 뜻은 "이 행동의 값어치"라 적립 여부와 무관하다.
     */
    private Long point;

    private Long referenceId;

    /** 자동 적립이 도는 요청에서 남기는 기록. 정가가 그대로 들어간다. */
    public static MissionLog from(MissionRegisterDto missionRegisterDto, User user) {
        return from(missionRegisterDto, user, true);
    }

    /**
     * @param accrued 이 요청에서 자동 적립이 도는가. 돌지 않으면 {@link #point} 를 0 으로 남겨
     *                하루 상한과 중복 방지 판정이 이 행을 "적립된 행"으로 세지 않게 한다.
     */
    public static MissionLog from(MissionRegisterDto missionRegisterDto, User user, boolean accrued) {
        return MissionLog.builder()
                .user(user)
                .missionType(missionRegisterDto.missionType())
                .point(accrued ? missionRegisterDto.missionType().getPoint() : 0L)
                .referenceId(missionRegisterDto.referenceId())
                .build();
    }
}
