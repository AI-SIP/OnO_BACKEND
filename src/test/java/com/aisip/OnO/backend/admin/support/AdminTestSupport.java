package com.aisip.OnO.backend.admin.support;

import com.aisip.OnO.backend.feedback.entity.UserFeedback;
import com.aisip.OnO.backend.feedback.repository.UserFeedbackRepository;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.repository.MissionLogRepository;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * admin 도메인 테스트의 공통 베이스.
 *
 * <p>{@link IntegrationTestSupport} 의 애노테이션 조합을 그대로 물려받는다.
 * {@code @MockBean} 을 새로 선언하면 스프링 컨텍스트가 갈라지므로 추가하지 않는다.
 *
 * <p>관리자 계정은 {@code AdminConfig} 의 {@code CommandLineRunner} 가 부팅 시 한 번 만들지만,
 * {@code DatabaseCleaner} 가 매 테스트 전에 user 테이블까지 비우기 때문에 테스트 시점에는 남아 있지 않다.
 * 관리자 계정이 필요한 테스트는 {@link #createAdminUser()} 로 직접 만들어야 한다.
 */
public abstract class AdminTestSupport extends IntegrationTestSupport {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ProblemRepository problemRepository;

    @Autowired
    protected PracticeNoteRepository practiceNoteRepository;

    @Autowired
    protected MissionLogRepository missionLogRepository;

    @Autowired
    protected UserFeedbackRepository userFeedbackRepository;

    @Autowired
    protected StudyRoomRepository studyRoomRepository;

    @Autowired
    protected StudyRoomMemberRepository studyRoomMemberRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Value("${admin.identifier}")
    protected String adminIdentifier;

    @Value("${admin.password}")
    protected String adminPassword;

    /** 설정에 적힌 관리자 자격증명으로 관리자 계정을 만든다. AdminConfig 가 부팅 시 만드는 것과 같은 형태다. */
    protected User createAdminUser() {
        return userRepository.save(User.from(new UserRegisterDto(
                "admin@ono.com",
                "Admin",
                adminIdentifier,
                "ADMIN",
                passwordEncoder.encode(adminPassword)
        )));
    }

    protected Problem saveProblem(Long userId, Folder folder, String memo) {
        Problem problem = Problem.from(
                new ProblemRegisterDto(null, memo, null, folder == null ? null : folder.getId(), null),
                userId
        );
        if (folder != null) {
            problem.updateFolder(folder);
        }
        return problemRepository.save(problem);
    }

    protected PracticeNote savePracticeNote(Long userId, String title) {
        return practiceNoteRepository.save(PracticeNote.from(
                new PracticeNoteRegisterDto(null, title, List.of(), null),
                userId
        ));
    }

    protected MissionLog saveMissionLog(User user, MissionType missionType, Long referenceId) {
        return missionLogRepository.save(MissionLog.from(
                com.aisip.OnO.backend.mission.dto.MissionRegisterDto.builder()
                        .userId(user.getId())
                        .missionType(missionType)
                        .referenceId(referenceId)
                        .build(),
                user
        ));
    }

    protected StudyRoom saveStudyRoom(String name, User host) {
        StudyRoom room = studyRoomRepository.save(StudyRoom.create(name, host.getId()));
        StudyRoomMember member = StudyRoomMember.create(host, StudyRoomMemberRole.HOST);
        member.updateRoom(room);
        studyRoomMemberRepository.save(member);
        return room;
    }

    protected UserFeedback saveFeedback(int npsScore, String usagePurpose) {
        return userFeedbackRepository.save(UserFeedback.builder()
                .npsScore(npsScore)
                .usagePurpose(usagePurpose)
                .submittedAt(LocalDateTime.now())
                .build());
    }

    /** created_at 은 BaseEntity 가 자동으로 채우므로, 과거 날짜가 필요하면 직접 밀어 넣는다. */
    protected void forceCreatedAt(String table, Long id, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE `" + table + "` SET created_at = ? WHERE id = ?", createdAt, id);
    }
}
