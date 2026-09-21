package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.Detail;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.Overview;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.RoomRow;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminStudyRoomController")
class AdminStudyRoomControllerTest extends AdminTestSupport {

    private User host;

    @BeforeEach
    void setUp() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
        host = fixtures.createUser();
    }

    @SuppressWarnings("unchecked")
    private List<RoomRow> roomsOf(MvcResult result) {
        return (List<RoomRow>) result.getModelAndView().getModel().get("rooms");
    }

    private Detail detailOf(MvcResult result) {
        return (Detail) result.getModelAndView().getModel().get("room");
    }

    private void addMember(StudyRoom room, User user) {
        StudyRoomMember member = StudyRoomMember.create(user, StudyRoomMemberRole.MEMBER);
        member.updateRoom(room);
        studyRoomMemberRepository.save(member);
    }

    private Long shareProblem(StudyRoom room, User sharer, Problem problem, String comment) {
        jdbcTemplate.update("""
                INSERT INTO study_room_shared_problem (created_at, updated_at, room_id, shared_by_user_id, problem_id, comment)
                VALUES (NOW(), NOW(), ?, ?, ?, ?)
                """, room.getId(), sharer.getId(), problem.getId(), comment);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM study_room_shared_problem", Long.class);
    }

    private void addComment(Long sharedProblemId, User author, String content) {
        jdbcTemplate.update("""
                INSERT INTO study_room_shared_problem_comment (created_at, updated_at, shared_problem_id, author_id, content)
                VALUES (NOW(), NOW(), ?, ?, ?)
                """, sharedProblemId, author.getId(), content);
    }

    private void addChallenge(StudyRoom room, String title, String status) {
        jdbcTemplate.update("""
                INSERT INTO study_room_challenge (created_at, updated_at, room_id, created_by_user_id, title, type, metric,
                                                  period, target_value, start_at, end_at, status)
                VALUES (NOW(), NOW(), ?, ?, ?, 'GROUP', 'PROBLEM_COUNT', 'WEEKLY', 10, NOW(), NOW() + INTERVAL 7 DAY, ?)
                """, room.getId(), host.getId(), title, status);
    }

    private void addFeed(StudyRoom room, User user, String eventType, String metadataJson, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO study_room_feed (created_at, updated_at, room_id, user_id, event_type, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """, createdAt, createdAt, room.getId(), user.getId(), eventType, metadataJson);
    }

    @Nested
    @DisplayName("스터디룸 목록")
    class RoomList {

        @Test
        @DisplayName("스터디룸이 없어도 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenEmpty() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/study-rooms"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-study-rooms"))
                    .andExpect(model().attribute("totalCount", 0L))
                    .andExpect(model().attribute("totalPages", 0))
                    .andReturn();

            assertThat(roomsOf(result)).isEmpty();
            Overview overview = (Overview) result.getModelAndView().getModel().get("overview");
            assertThat(overview.totalRooms()).isZero();
            assertThat(overview.activeRoomsThisWeek()).isZero();
        }

        @Test
        @DisplayName("방마다 방장 이름, 멤버, 공유 문제, 댓글, 챌린지, 최근 활동을 집계한다")
        void aggregatesRoomActivity() throws Exception {
            StudyRoom room = saveStudyRoom("고3 수학방", host);
            User member = fixtures.createOtherUser();
            addMember(room, member);
            Long shared = shareProblem(room, member, saveProblem(member.getId(), null, "메모"), "같이 풀어요");
            addComment(shared, host, "좋아요");
            addComment(shared, member, "감사해요");
            addChallenge(room, "주간 10문제", "IN_PROGRESS");
            addChallenge(room, "지난주", "COMPLETED");
            LocalDateTime feedAt = LocalDateTime.now().withNano(0);
            addFeed(room, member, "PRACTICE_COMPLETED", "{\"count\": 3}", feedAt);

            MvcResult result = mockMvc.perform(get("/admin/study-rooms"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalCount", 1L))
                    .andReturn();

            assertThat(roomsOf(result)).singleElement().satisfies(r -> {
                assertThat(r.id()).isEqualTo(room.getId());
                assertThat(r.name()).isEqualTo("고3 수학방");
                assertThat(r.hostUserId()).isEqualTo(host.getId());
                assertThat(r.hostName()).isEqualTo(host.getName());
                assertThat(r.memberCount()).isEqualTo(2L);
                assertThat(r.sharedProblemCount()).isEqualTo(1L);
                assertThat(r.commentCount()).isEqualTo(2L);
                assertThat(r.inProgressChallengeCount()).isEqualTo(1L);
                assertThat(r.completedChallengeCount()).isEqualTo(1L);
                assertThat(r.lastActivityAt()).isEqualTo(feedAt);
            });

            Overview overview = (Overview) result.getModelAndView().getModel().get("overview");
            assertThat(overview.totalRooms()).isEqualTo(1L);
            assertThat(overview.totalMembers()).isEqualTo(2L);
            assertThat(overview.sharedProblems()).isEqualTo(1L);
            assertThat(overview.inProgressChallenges()).isEqualTo(1L);
            assertThat(overview.activeRoomsThisWeek()).isEqualTo(1L);
        }

        @Test
        @DisplayName("활동이 없는 방은 집계 칸이 null 이 아니라 0 이다")
        void zeroForRoomWithoutActivity() throws Exception {
            saveStudyRoom("빈 방", host);

            MvcResult result = mockMvc.perform(get("/admin/study-rooms")).andReturn();

            assertThat(roomsOf(result)).singleElement().satisfies(r -> {
                assertThat(r.memberCount()).isEqualTo(1L);
                assertThat(r.sharedProblemCount()).isZero();
                assertThat(r.commentCount()).isZero();
                assertThat(r.lastActivityAt()).isNull();
            });
        }

        @Test
        @DisplayName("size 로 페이지를 끊고, 다음 페이지 링크에 page 만 바뀐다")
        void paginatesRooms() throws Exception {
            saveStudyRoom("방1", host);
            saveStudyRoom("방2", host);
            saveStudyRoom("방3", host);

            // 페이지 링크는 쿼리스트링에서 만든다. MockMvc 의 param() 은 쿼리스트링을 채우지 않아서 URL 에 직접 붙인다.
            MvcResult result = mockMvc.perform(get("/admin/study-rooms?size=2"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPages", 2))
                    .andReturn();

            assertThat(roomsOf(result)).hasSize(2);
            com.aisip.OnO.backend.admin.dto.AdminPager pager =
                    (com.aisip.OnO.backend.admin.dto.AdminPager) result.getModelAndView().getModel().get("pager");
            assertThat(pager.nextUrl()).contains("page=1").contains("size=2");
        }

        @ParameterizedTest(name = "page={0}")
        @ValueSource(ints = {-1, -20})
        @DisplayName("음수 page 는 500이 아니라 0페이지로 보정한다")
        void clampsNegativePage(int page) throws Exception {
            saveStudyRoom("방", host);

            mockMvc.perform(get("/admin/study-rooms").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentPage", 0));
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("0 이하 size 는 500이 아니라 1로 보정한다")
        void clampsNonPositiveSize(int size) throws Exception {
            saveStudyRoom("방", host);

            mockMvc.perform(get("/admin/study-rooms").param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("size", 1));
        }

        @Test
        @DisplayName("size 가 과도하게 커도 500을 내지 않고 상한으로 자른다")
        void capsOversizedPageSize() throws Exception {
            saveStudyRoom("방", host);

            mockMvc.perform(get("/admin/study-rooms").param("size", "100000"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPages", 1))
                    .andExpect(model().attribute("size", 200));
        }
    }

    @Nested
    @DisplayName("스터디룸 상세")
    class RoomDetail {

        @Test
        @DisplayName("방장과 멤버, 멤버별 공유 문제 수를 보여준다")
        void showsMembers() throws Exception {
            StudyRoom room = saveStudyRoom("고3 수학방", host);
            User member = fixtures.createOtherUser();
            addMember(room, member);
            shareProblem(room, member, saveProblem(member.getId(), null, "메모"), null);

            MvcResult result = mockMvc.perform(get("/admin/study-rooms/{id}", room.getId()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-study-room-detail"))
                    .andReturn();

            Detail detail = detailOf(result);
            assertThat(detail.room().id()).isEqualTo(room.getId());
            assertThat(detail.room().hostUserId()).isEqualTo(host.getId());
            assertThat(detail.members()).extracting(m -> m.userId())
                    .as("방장이 먼저 온다")
                    .containsExactly(host.getId(), member.getId());
            assertThat(detail.members().get(0).host()).isTrue();
            assertThat(detail.members().get(1).sharedProblemCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("공유 문제마다 댓글을 붙이고, 챌린지와 피드를 한국어 라벨로 보여준다")
        void showsSharedProblemsChallengesAndFeeds() throws Exception {
            StudyRoom room = saveStudyRoom("방", host);
            Problem problem = saveProblem(host.getId(), null, "메모");
            Long shared = shareProblem(room, host, problem, "이거 어려워요");
            addComment(shared, host, "첫 댓글");
            addChallenge(room, "주간 10문제", "IN_PROGRESS");
            addFeed(room, host, "LEVEL_UP", "{\"ability\": \"PROBLEM_PRACTICE\", \"level\": 8}", LocalDateTime.now());
            addFeed(room, host, "PROBLEM_REGISTERED", "깨진 JSON", LocalDateTime.now().minusMinutes(1));

            Detail detail = detailOf(mockMvc.perform(get("/admin/study-rooms/{id}", room.getId()))
                    .andExpect(status().isOk())
                    .andReturn());

            assertThat(detail.sharedProblems()).singleElement().satisfies(sp -> {
                assertThat(sp.problemId()).isEqualTo(problem.getId());
                assertThat(sp.comment()).isEqualTo("이거 어려워요");
                assertThat(sp.commentCount()).isEqualTo(1L);
                assertThat(sp.comments()).extracting(c -> c.content()).containsExactly("첫 댓글");
            });
            assertThat(detail.totalCommentCount()).isEqualTo(1L);
            assertThat(detail.challenges()).singleElement().satisfies(c -> {
                assertThat(c.statusLabel()).isEqualTo("진행 중");
                assertThat(c.typeLabel()).isEqualTo("그룹");
                assertThat(c.metricLabel()).isEqualTo("오답노트 등록");
            });
            assertThat(detail.feeds()).hasSize(2);
            assertThat(detail.feeds().get(0).eventLabel()).isEqualTo("레벨 업");
            assertThat(detail.feeds().get(0).summary()).isEqualTo("문제 복습 Lv.8");
            assertThat(detail.feeds().get(1).summary())
                    .as("메타데이터가 깨져 있어도 화면은 열려야 한다")
                    .isEmpty();
        }

        @Test
        @DisplayName("다른 방의 데이터는 섞이지 않는다")
        void doesNotMixOtherRooms() throws Exception {
            StudyRoom room = saveStudyRoom("내 방", host);
            User other = fixtures.createOtherUser();
            StudyRoom otherRoom = saveStudyRoom("남의 방", other);
            shareProblem(otherRoom, other, saveProblem(other.getId(), null, "메모"), null);
            addChallenge(otherRoom, "남의 챌린지", "IN_PROGRESS");

            Detail detail = detailOf(mockMvc.perform(get("/admin/study-rooms/{id}", room.getId())).andReturn());

            assertThat(detail.members()).extracting(m -> m.userId()).containsExactly(host.getId());
            assertThat(detail.sharedProblems()).isEmpty();
            assertThat(detail.challenges()).isEmpty();
        }

        @Test
        @DisplayName("없는 스터디룸을 조회하면 500이 아니라 404로 응답한다")
        void returnsNotFoundForUnknownRoom() throws Exception {
            mockMvc.perform(get("/admin/study-rooms/{id}", 999_999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("id 가 숫자가 아니면 400으로 거절한다")
        void rejectsNonNumericId() throws Exception {
            mockMvc.perform(get("/admin/study-rooms/{id}", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }
}
