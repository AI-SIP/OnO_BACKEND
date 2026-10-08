package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.practicenote.repository.ProblemPracticeNoteMappingRepository;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.tag.dto.TagDeleteRequestDto;
import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.service.TagService;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 연관 데이터가 실제로 붙어 있는 상태에서 지우는 경로를 DB 행 기준으로 확인한다.
 *
 * <p>Hibernate 6.6 부터 flush 때 cascade 가 이미 지운 자식을 되살리거나(un-schedule),
 * 지운 엔티티를 가리키는 관리 엔티티가 남아 있으면 예외를 던진다. 기존 테스트는 대부분
 * 이미지, 태그, 복습노트 없이 지우는 경로만 덮고 있어서, 이 조합이 실제로 남김없이 지워지는지
 * 따로 본다. 모든 엔티티가 소프트 삭제라 {@code deleted_at IS NULL} 인 행이 남았는지로 판정한다.
 */
@DisplayName("연관 데이터가 붙은 삭제")
class DeleteWithRelationsTest extends ProblemTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private FolderService folderService;

    @Autowired
    private TagService tagService;

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    @Autowired
    private ProblemPracticeNoteMappingRepository problemPracticeNoteMappingRepository;

    @Autowired
    private StudyRoomRepository roomRepository;

    @Autowired
    private StudyRoomMemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private Folder root;
    private Folder sub;
    private Problem rootProblem;
    private Problem subProblem;
    private Tag tag;

    @BeforeEach
    void setUp() {
        owner = fixtures.createUser("owner");
        root = fixtures.createRootFolder(owner.getId());
        sub = fixtures.createFolder(owner.getId(), "하위", root);

        rootProblem = saveProblem(owner.getId(), root);
        subProblem = saveProblem(owner.getId(), sub);
        tag = saveTag(owner.getId(), "함수");
        for (Problem problem : List.of(rootProblem, subProblem)) {
            saveImageData(problem, "https://s3/" + problem.getId() + "-p.png", ProblemImageType.PROBLEM_IMAGE);
            saveImageData(problem, "https://s3/" + problem.getId() + "-a.png", ProblemImageType.ANSWER_IMAGE);
            saveTagMapping(problem, tag);
        }
        savePracticeNote(owner.getId(), List.of(rootProblem, subProblem));
    }

    @Test
    @DisplayName("이미지, 태그, 복습노트, 폴더, 스터디룸이 모두 있는 사용자가 탈퇴하면 남김없이 정리된다")
    void withdrawalCleansEverything() {
        User other = fixtures.createUser("other");
        StudyRoom aloneRoom = saveRoom(owner);
        StudyRoom othersRoom = saveRoom(other);
        addMember(othersRoom, owner);

        userService.deleteUserById(owner.getId());

        List<Long> problemIds = List.of(rootProblem.getId(), subProblem.getId());
        assertThat(liveProblemsOfOwner()).as("문제").isZero();
        assertThat(liveRowsByProblem("image_data", problemIds)).as("문제 이미지").isZero();
        assertThat(liveRowsByProblem("problem_tag_mapping", problemIds)).as("태그 매핑").isZero();
        assertThat(liveRowsByProblem("problem_practice_note_mapping", problemIds)).as("복습노트 매핑").isZero();
        assertThat(count("select count(*) from practice_note where user_id = ? and deleted_at is null", owner.getId()))
                .as("복습노트").isZero();
        assertThat(count("select count(*) from folder where user_id = ? and deleted_at is null", owner.getId()))
                .as("폴더").isZero();
        assertThat(count("select count(*) from user where id = ? and deleted_at is not null", owner.getId()))
                .as("사용자는 소프트 삭제된다").isEqualTo(1);
        assertThat(memberRepository.countByUserId(owner.getId())).as("멤버십").isZero();
        assertThat(roomRepository.findById(aloneRoom.getId())).as("혼자 연 방").isEmpty();
        assertThat(memberRepository.countByRoomId(othersRoom.getId())).as("남의 방에는 방장만 남는다").isEqualTo(1);
    }

    @Test
    @DisplayName("이미지가 붙은 문제가 든 폴더를 지우면 그 폴더의 문제와 이미지, 매핑만 지워진다")
    void folderDeletionCleansImagesOfItsProblemsOnly() {
        folderService.deleteFoldersWithProblems(owner.getId(), List.of(sub.getId()));

        assertThat(liveRowsByProblem("image_data", List.of(subProblem.getId()))).as("지운 폴더의 이미지").isZero();
        assertThat(liveRowsByProblem("problem_tag_mapping", List.of(subProblem.getId()))).as("지운 폴더의 태그 매핑").isZero();
        assertThat(liveRowsByProblem("problem_practice_note_mapping", List.of(subProblem.getId())))
                .as("지운 폴더의 복습노트 매핑").isZero();

        assertThat(liveRowsByProblem("image_data", List.of(rootProblem.getId()))).as("남은 폴더의 이미지").isEqualTo(2);
        assertThat(liveRowsByProblem("problem_tag_mapping", List.of(rootProblem.getId()))).as("남은 폴더의 태그 매핑").isEqualTo(1);
        assertThat(liveRowsByProblem("problem_practice_note_mapping", List.of(rootProblem.getId())))
                .as("남은 폴더의 복습노트 매핑").isEqualTo(1);
    }

    @Test
    @DisplayName("문제에 붙은 태그를 지우면 태그와 매핑이 지워지고 문제는 남는다")
    void tagDeletionWithMappings() {
        tagService.deleteTags(owner.getId(), new TagDeleteRequestDto(List.of(tag.getId())));

        List<Long> problemIds = List.of(rootProblem.getId(), subProblem.getId());
        assertThat(count("select count(*) from tag where id = ? and deleted_at is null", tag.getId())).as("태그").isZero();
        assertThat(liveRowsByProblem("problem_tag_mapping", problemIds)).as("태그 매핑").isZero();
        assertThat(liveProblemsOfOwner()).as("문제").isEqualTo(2);
        assertThat(liveRowsByProblem("image_data", problemIds)).as("이미지").isEqualTo(4);
    }

    private PracticeNote savePracticeNote(Long userId, List<Problem> problems) {
        PracticeNote practiceNote = practiceNoteRepository.save(PracticeNote.from(
                new PracticeNoteRegisterDto(null, "복습", List.of(), null), userId));
        problems.forEach(problem -> {
            ProblemPracticeNoteMapping mapping = ProblemPracticeNoteMapping.from();
            mapping.addMappingToProblemAndPractice(problem, practiceNote);
            problemPracticeNoteMappingRepository.save(mapping);
        });
        return practiceNote;
    }

    private StudyRoom saveRoom(User host) {
        StudyRoom room = StudyRoom.create("방-" + host.getId(), host.getId());
        room.addMember(StudyRoomMember.create(host, StudyRoomMemberRole.HOST));
        return roomRepository.save(room);
    }

    private void addMember(StudyRoom room, User user) {
        StudyRoomMember member = StudyRoomMember.create(user, StudyRoomMemberRole.MEMBER);
        member.updateRoom(room);
        memberRepository.saveAndFlush(member);
    }

    private long liveProblemsOfOwner() {
        return count("select count(*) from problem where user_id = ? and deleted_at is null", owner.getId());
    }

    private long liveRowsByProblem(String table, List<Long> problemIds) {
        String in = String.join(",", problemIds.stream().map(String::valueOf).toList());
        return count("select count(*) from " + table + " where problem_id in (" + in + ") and deleted_at is null");
    }

    private long count(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }
}
