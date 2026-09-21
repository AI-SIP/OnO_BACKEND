package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminPager;
import com.aisip.OnO.backend.admin.dto.AdminUserRows;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminUserController")
class AdminUserControllerTest extends AdminTestSupport {

    private User admin;

    @BeforeEach
    void loginAsAdmin() {
        admin = createAdminUser();
        authenticateAs(admin.getId(), "ROLE_ADMIN");
    }

    @SuppressWarnings("unchecked")
    private List<AdminUserRows.ListRow> usersOf(MvcResult result) {
        return (List<AdminUserRows.ListRow>) result.getModelAndView().getModel().get("users");
    }

    private AdminPager pagerOf(MvcResult result) {
        return (AdminPager) result.getModelAndView().getModel().get("pager");
    }

    private Object modelOf(MvcResult result, String name) {
        return result.getModelAndView().getModel().get(name);
    }

    private void saveSolve(Long userId, Long problemId, String answerStatus, LocalDateTime practicedAt) {
        jdbcTemplate.update("""
                INSERT INTO problem_solve (problem_id, user_id, practiced_at, answer_status, migrated_from_legacy, created_at, updated_at)
                VALUES (?, ?, ?, ?, false, NOW(), NOW())
                """, problemId, userId, practicedAt, answerStatus);
    }

    @Nested
    @DisplayName("사용자 목록")
    class UserList {

        @Test
        @DisplayName("사용자가 하나도 없어도 500이 아니라 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenNoUserExists() throws Exception {
            // User 는 소프트 삭제라 deleteAll() 로는 행이 남는다. 집계가 0인 상황을 만들려면 물리 삭제해야 한다.
            jdbcTemplate.update("DELETE FROM `user`");

            MvcResult result = mockMvc.perform(get("/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("users"))
                    .andExpect(model().attribute("totalUsers", 0L))
                    .andReturn();

            AdminPager pager = pagerOf(result);
            assertThat(pager.totalPages()).isZero();
            assertThat(pager.startItem()).isZero();
            assertThat(pager.endItem()).isZero();
            assertThat(pager.previousUrl()).isNull();
            assertThat(pager.nextUrl()).isNull();
            assertThat(((AdminUserRows.Summary) modelOf(result, "summary")).totalUsers()).isZero();
        }

        @Test
        @DisplayName("요약 숫자에서 게스트와 관리자는 빠지지만 목록에는 모두 나온다")
        void excludesGuestFromSummary() throws Exception {
            User member = fixtures.createUser();
            User guest = createGuestUser();
            saveMissionLog(member, com.aisip.OnO.backend.mission.entity.MissionType.USER_LOGIN, null);
            saveMissionLog(guest, com.aisip.OnO.backend.mission.entity.MissionType.USER_LOGIN, null);

            MvcResult result = mockMvc.perform(get("/admin/users")).andExpect(status().isOk()).andReturn();

            AdminUserRows.Summary summary = (AdminUserRows.Summary) modelOf(result, "summary");
            assertThat(summary.totalUsers()).isEqualTo(1);
            assertThat(summary.todaySignups()).isEqualTo(1);
            assertThat(summary.activeUsersLast7Days()).isEqualTo(1);
            assertThat(result.getModelAndView().getModel().get("totalUsers"))
                    .as("목록은 집계가 아니라서 관리자와 게스트도 센다")
                    .isEqualTo(3L);
        }

        @Test
        @DisplayName("전체 사용자를 페이지 단위로 끊어서 보여주고, 다음 페이지 링크에 조건이 유지된다")
        void paginatesUsers() throws Exception {
            fixtures.createUser();
            fixtures.createUser();
            fixtures.createUser();

            // 페이지 링크는 요청의 쿼리스트링으로 만든다. param() 은 쿼리스트링을 채우지 않아 URL 에 직접 넣는다.
            MvcResult firstPage = mockMvc.perform(get("/admin/users?size=2&sortBy=name"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalUsers", 4L))
                    .andExpect(model().attribute("currentPage", 0))
                    .andReturn();

            AdminPager pager = pagerOf(firstPage);
            assertThat(pager.totalPages()).isEqualTo(2);
            assertThat(pager.startItem()).isEqualTo(1);
            assertThat(pager.endItem()).isEqualTo(2);
            assertThat(pager.nextUrl())
                    .as("다음 페이지로 넘어가도 정렬 조건이 풀리면 안 된다")
                    .contains("page=1")
                    .contains("sortBy=name");
            assertThat(usersOf(firstPage)).hasSize(2);

            MvcResult secondPage = mockMvc.perform(get("/admin/users?page=1&size=2&sortBy=name"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(usersOf(secondPage)).hasSize(2);
            assertThat(usersOf(secondPage))
                    .as("페이지가 겹치면 같은 사용자가 두 번 보인다")
                    .extracting(AdminUserRows.ListRow::userId)
                    .doesNotContainAnyElementsOf(usersOf(firstPage).stream().map(AdminUserRows.ListRow::userId).toList());
        }

        @Test
        @DisplayName("사용자마다 문제, 복습 기록, 복습노트 수를 함께 집계한다")
        void aggregatesOwnedDataPerUser() throws Exception {
            User target = fixtures.createUser();
            Folder folder = fixtures.createRootFolder(target.getId());
            Problem problem = saveProblem(target.getId(), folder, "문제1");
            saveProblem(target.getId(), folder, "문제2");
            saveSolve(target.getId(), problem.getId(), "CORRECT", LocalDateTime.now());
            savePracticeNote(target.getId(), "복습노트");

            MvcResult result = mockMvc.perform(get("/admin/users").param("size", "50"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(usersOf(result))
                    .filteredOn(dto -> dto.userId().equals(target.getId()))
                    .singleElement()
                    .satisfies(dto -> {
                        assertThat(dto.problemCount()).isEqualTo(2L);
                        assertThat(dto.solveCount()).isEqualTo(1L);
                        assertThat(dto.practiceNoteCount()).isEqualTo(1L);
                        assertThat(dto.totalStudyLevel()).as("신규 사용자는 레벨 1에서 시작한다").isEqualTo(1L);
                    });

            assertThat(usersOf(result))
                    .filteredOn(dto -> dto.userId().equals(admin.getId()))
                    .singleElement()
                    .satisfies(dto -> assertThat(dto.problemCount())
                            .as("문제가 없는 사용자는 null 이 아니라 0 이어야 한다")
                            .isEqualTo(0L));
        }

        @Test
        @DisplayName("이름이나 이메일 일부로 검색한다")
        void searchesByNameOrEmail() throws Exception {
            User target = fixtures.createUser("searchme");
            fixtures.createUser("someone");

            MvcResult byName = mockMvc.perform(get("/admin/users").param("q", "searchme"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalUsers", 1L))
                    .andExpect(model().attribute("q", "searchme"))
                    .andReturn();
            assertThat(usersOf(byName)).extracting(AdminUserRows.ListRow::userId).containsExactly(target.getId());

            MvcResult byEmail = mockMvc.perform(get("/admin/users").param("q", target.getEmail()))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(usersOf(byEmail)).extracting(AdminUserRows.ListRow::userId).containsExactly(target.getId());
        }

        @Test
        @DisplayName("숫자로 검색하면 유저 ID 가 같은 사람도 찾는다")
        void searchesById() throws Exception {
            User target = fixtures.createUser();

            MvcResult result = mockMvc.perform(get("/admin/users").param("q", String.valueOf(target.getId())))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(usersOf(result)).extracting(AdminUserRows.ListRow::userId).contains(target.getId());
        }

        @Test
        @DisplayName("검색어의 % 는 와일드카드가 아니라 글자로 취급한다")
        void escapesLikeWildcard() throws Exception {
            fixtures.createUser();

            mockMvc.perform(get("/admin/users").param("q", "%"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalUsers", 0L));
        }

        @Test
        @DisplayName("가입 경로로 거르고, 대소문자가 달라도 같은 경로로 본다")
        void filtersByPlatform() throws Exception {
            User googleUser = fixtures.createUser();

            MvcResult result = mockMvc.perform(get("/admin/users").param("platform", "GOOGLE"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalUsers", 1L))
                    .andReturn();

            assertThat(usersOf(result)).extracting(AdminUserRows.ListRow::userId).containsExactly(googleUser.getId());
            @SuppressWarnings("unchecked")
            List<String> platforms = (List<String>) modelOf(result, "platforms");
            assertThat(platforms).contains("GOOGLE", "ADMIN");
        }

        @Test
        @DisplayName("오답노트 수로 정렬할 수 있다")
        void sortsByProblemCount() throws Exception {
            User heavy = fixtures.createUser();
            Folder folder = fixtures.createRootFolder(heavy.getId());
            saveProblem(heavy.getId(), folder, "1");
            saveProblem(heavy.getId(), folder, "2");
            fixtures.createUser();

            MvcResult result = mockMvc.perform(get("/admin/users").param("sortBy", "problemCount").param("direction", "desc"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(usersOf(result).get(0).userId()).isEqualTo(heavy.getId());
        }

        @Test
        @DisplayName("모르는 정렬 기준은 SQL 에 붙지 않고 가입일 정렬로 처리한다")
        void ignoresUnknownSortColumn() throws Exception {
            fixtures.createUser();

            mockMvc.perform(get("/admin/users").param("sortBy", "id; DROP TABLE user").param("direction", "sideways"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalUsers", 2L));
        }

        @Test
        @DisplayName("요약에는 오늘 가입자와 최근 7일 출석 유저가 들어간다")
        void summarizesUsers() throws Exception {
            User target = fixtures.createUser();
            saveMissionLog(target, MissionType.USER_LOGIN, null);

            MvcResult result = mockMvc.perform(get("/admin/users"))
                    .andExpect(status().isOk())
                    .andReturn();

            AdminUserRows.Summary summary = (AdminUserRows.Summary) modelOf(result, "summary");
            assertThat(summary.totalUsers()).as("관리자 계정은 세지 않는다").isEqualTo(1L);
            assertThat(summary.todaySignups()).isEqualTo(1L);
            assertThat(summary.activeUsersLast7Days()).isEqualTo(1L);
        }

        @ParameterizedTest(name = "page={0} 는 0페이지로 보정된다")
        @ValueSource(ints = {-1, -100, Integer.MIN_VALUE})
        @DisplayName("음수 page 는 500을 내지 않고 0페이지로 보정한다")
        void clampsNegativePage(int page) throws Exception {
            fixtures.createUser();

            mockMvc.perform(get("/admin/users").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentPage", 0));
        }

        @ParameterizedTest(name = "size={0} 은 1 이상으로 보정된다")
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        @DisplayName("0 이하 size 는 500을 내지 않고 1로 보정한다")
        void clampsNonPositiveSize(int size) throws Exception {
            fixtures.createUser();

            mockMvc.perform(get("/admin/users").param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("size", 1));
        }

        @Test
        @DisplayName("size 가 너무 크면 상한으로 줄이되 한 페이지에 전부 담아 준다")
        void capsOversizedPageSize() throws Exception {
            fixtures.createUser();
            fixtures.createUser();

            MvcResult result = mockMvc.perform(get("/admin/users").param("size", "10000"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("size", 500))
                    .andReturn();

            assertThat(pagerOf(result).totalPages()).isEqualTo(1);
            assertThat(usersOf(result)).hasSize(3);
        }

        @Test
        @DisplayName("전체 페이지 수를 넘는 page 를 요청해도 빈 목록으로 응답한다")
        void returnsEmptyListBeyondLastPage() throws Exception {
            fixtures.createUser();

            MvcResult result = mockMvc.perform(get("/admin/users").param("page", "500").param("size", "20"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(usersOf(result)).isEmpty();
        }

        @Test
        @DisplayName("정렬 파라미터를 그대로 모델에 실어 화면 상태를 유지한다")
        void keepsSortParametersInModel() throws Exception {
            mockMvc.perform(get("/admin/users").param("sortBy", "name").param("direction", "asc"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("sortBy", "name"))
                    .andExpect(model().attribute("direction", "asc"));
        }
    }

    @Nested
    @DisplayName("사용자 상세")
    class UserDetail {

        @Test
        @DisplayName("사용자의 문제, 폴더, 복습노트, 미션 기록을 모두 모아 보여준다")
        void showsAllOwnedDataOfUser() throws Exception {
            User target = fixtures.createUser();
            Folder folder = fixtures.createRootFolder(target.getId());
            Problem problem = saveProblem(target.getId(), folder, "메모");
            savePracticeNote(target.getId(), "복습노트");
            saveMissionLog(target, MissionType.USER_LOGIN, null);
            saveSolve(target.getId(), problem.getId(), "CORRECT", LocalDateTime.now());
            saveSolve(target.getId(), problem.getId(), "WRONG", LocalDateTime.now().minusDays(1));

            MvcResult result = mockMvc.perform(get("/admin/user/{userId}", target.getId()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user"))
                    .andReturn();

            AdminUserRows.Counts counts = (AdminUserRows.Counts) modelOf(result, "counts");
            assertThat(counts.problemCount()).isEqualTo(1L);
            assertThat(counts.folderCount()).isEqualTo(1L);
            assertThat(counts.practiceNoteCount()).isEqualTo(1L);
            assertThat(counts.solveCount()).isEqualTo(2L);
            assertThat(counts.correctRate()).isEqualTo(50.0);
            assertThat(counts.loginDayCount()).isEqualTo(1L);
            assertThat((List<?>) modelOf(result, "missionLogs")).hasSize(1);
            assertThat(modelOf(result, "loginStreak")).isEqualTo(1);

            @SuppressWarnings("unchecked")
            List<AdminUserRows.ProblemRow> problems = (List<AdminUserRows.ProblemRow>) modelOf(result, "problems");
            assertThat(problems).singleElement().satisfies(row -> {
                assertThat(row.solveCount()).isEqualTo(2L);
                assertThat(row.folderName()).isEqualTo("루트 폴더");
            });

            @SuppressWarnings("unchecked")
            List<AdminUserRows.SolveRow> solves = (List<AdminUserRows.SolveRow>) modelOf(result, "solves");
            assertThat(solves).extracting(AdminUserRows.SolveRow::answerStatus)
                    .as("최근 복습이 먼저 나와야 한다")
                    .containsExactly("CORRECT", "WRONG");
        }

        @Test
        @DisplayName("착용한 치장과 받은 업적, 참여한 스터디룸을 보여준다")
        void showsCosmeticsAchievementsAndRooms() throws Exception {
            User target = fixtures.createUser();
            jdbcTemplate.update("INSERT INTO user_cosmetic_loadout (user_id, slot, item_key, updated_at) VALUES (?, 'HEAD', 'headband_sprout', NOW())", target.getId());
            jdbcTemplate.update("INSERT INTO user_achievement (user_id, achievement_key, earned_at) VALUES (?, 'first_step', NOW())", target.getId());
            jdbcTemplate.update("INSERT INTO user_achievement (user_id, achievement_key, earned_at) VALUES (?, 'removed_medal', NOW())", target.getId());
            saveStudyRoom("같이 공부방", target);

            MvcResult result = mockMvc.perform(get("/admin/user/{userId}", target.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            @SuppressWarnings("unchecked")
            List<AdminUserRows.CosmeticRow> cosmetics = (List<AdminUserRows.CosmeticRow>) modelOf(result, "cosmetics");
            assertThat(cosmetics).singleElement().satisfies(row -> {
                assertThat(row.slotName()).isEqualTo("머리");
                assertThat(row.itemKey()).isEqualTo("headband_sprout");
            });

            @SuppressWarnings("unchecked")
            List<AdminUserRows.AchievementRow> achievements = (List<AdminUserRows.AchievementRow>) modelOf(result, "achievements");
            assertThat(achievements).extracting(AdminUserRows.AchievementRow::name)
                    .as("배포에서 빠진 업적 키도 화면이 깨지지 않고 키 그대로 보여야 한다")
                    .containsExactlyInAnyOrder("첫 걸음", "removed_medal");

            @SuppressWarnings("unchecked")
            List<AdminUserRows.StudyRoomRow> rooms = (List<AdminUserRows.StudyRoomRow>) modelOf(result, "studyRooms");
            assertThat(rooms).singleElement().satisfies(row -> {
                assertThat(row.name()).isEqualTo("같이 공부방");
                assertThat(row.role()).isEqualTo("HOST");
                assertThat(row.memberCount()).isEqualTo(1L);
            });
        }

        @Test
        @DisplayName("일간 미션 진행도를 미션 정의와 묶어서 보여준다")
        void showsMissionProgress() throws Exception {
            User target = fixtures.createUser();
            jdbcTemplate.update("""
                    INSERT INTO mission_definition (code, title, description, icon_key, category, metric, target,
                                                    reward_type, reward_value, sort_order, active, created_at, updated_at)
                    VALUES ('DAILY_ATTEND', '출석', '앱 열기', 'attend', 'DAILY', 'LOGIN_DAY', 1, 'XP', 10, 1, true, NOW(), NOW())
                    """);
            Long missionId = jdbcTemplate.queryForObject("SELECT id FROM mission_definition WHERE code = 'DAILY_ATTEND'", Long.class);
            jdbcTemplate.update("""
                    INSERT INTO mission_progress (user_id, mission_id, period_key, current_value, target_snapshot,
                                                  completed_at, created_at, updated_at)
                    VALUES (?, ?, '2026-09-20', 1, 1, NOW(), NOW(), NOW())
                    """, target.getId(), missionId);

            MvcResult result = mockMvc.perform(get("/admin/user/{userId}", target.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            @SuppressWarnings("unchecked")
            List<AdminUserRows.MissionProgressRow> progress = (List<AdminUserRows.MissionProgressRow>) modelOf(result, "missionProgress");
            assertThat(progress).singleElement().satisfies(row -> {
                assertThat(row.title()).isEqualTo("출석");
                assertThat(row.currentValue()).isEqualTo(1);
                assertThat(row.completedAt()).isNotNull();
                assertThat(row.claimedAt()).isNull();
                assertThat(row.rewardType()).as("스냅숏이 비어 있으면 정의의 보상을 보여 준다").isEqualTo("XP");
            });
        }

        @Test
        @DisplayName("다른 사용자의 데이터는 섞이지 않는다")
        void doesNotMixDataOfOtherUsers() throws Exception {
            User target = fixtures.createUser();
            User other = fixtures.createOtherUser();
            Folder otherFolder = fixtures.createRootFolder(other.getId());
            Problem otherProblem = saveProblem(other.getId(), otherFolder, "남의 문제");
            saveSolve(other.getId(), otherProblem.getId(), "CORRECT", LocalDateTime.now());
            jdbcTemplate.update("INSERT INTO user_achievement (user_id, achievement_key, earned_at) VALUES (?, 'first_step', NOW())", other.getId());

            MvcResult result = mockMvc.perform(get("/admin/user/{userId}", target.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            AdminUserRows.Counts counts = (AdminUserRows.Counts) modelOf(result, "counts");
            assertThat(counts.problemCount()).isZero();
            assertThat(counts.folderCount()).isZero();
            assertThat(counts.solveCount()).isZero();
            assertThat(counts.correctRate()).as("복습이 없을 때 0으로 나누면 안 된다").isZero();
            assertThat((List<?>) modelOf(result, "achievements")).isEmpty();
            assertThat((List<?>) modelOf(result, "solves")).isEmpty();
        }

        @Test
        @DisplayName("없는 사용자를 조회하면 500이 아니라 404로 응답한다")
        void returnsNotFoundForUnknownUser() throws Exception {
            mockMvc.perform(get("/admin/user/{userId}", 999_999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("탈퇴한 사용자는 상세 화면을 열 수 없다")
        void returnsNotFoundForWithdrawnUser() throws Exception {
            User target = fixtures.createUser();
            jdbcTemplate.update("UPDATE `user` SET deleted_at = NOW() WHERE id = ?", target.getId());

            mockMvc.perform(get("/admin/user/{userId}", target.getId()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("연속 출석 계산")
    class LoginStreak {

        @Test
        @DisplayName("오늘부터 끊기지 않고 이어진 날만 센다")
        void countsConsecutiveDaysFromToday() {
            LocalDate today = LocalDate.now();

            assertThat(AdminUserController.currentStreak(List.of(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))))
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("오늘 아직 안 들어왔어도 어제까지 이어졌으면 인정한다")
        void acceptsStreakEndingYesterday() {
            LocalDate today = LocalDate.now();

            assertThat(AdminUserController.currentStreak(List.of(today.minusDays(1), today.minusDays(2)))).isEqualTo(2);
        }

        @Test
        @DisplayName("마지막 출석이 그저께보다 오래됐으면 0이다")
        void resetsWhenBroken() {
            LocalDate today = LocalDate.now();

            assertThat(AdminUserController.currentStreak(List.of(today.minusDays(2)))).isZero();
            assertThat(AdminUserController.currentStreak(List.of())).isZero();
        }
    }

    @Nested
    @DisplayName("사용자 수정")
    class UpdateUser {

        @Test
        @DisplayName("이름, 이메일을 수정하면 저장하고 상세 화면으로 리다이렉트한다")
        void updatesUserAndRedirects() throws Exception {
            User target = fixtures.createUser();

            mockMvc.perform(post("/admin/user/{userId}", target.getId())
                            .param("name", "바뀐이름")
                            .param("email", "changed@test.ono"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/user/" + target.getId()));

            assertThat(userRepository.findById(target.getId()))
                    .get()
                    .satisfies(user -> {
                        assertThat(user.getName()).isEqualTo("바뀐이름");
                        assertThat(user.getEmail()).isEqualTo("changed@test.ono");
                    });
        }

        @Test
        @DisplayName("빈 값으로 보낸 항목은 기존 값을 덮어쓰지 않는다")
        void keepsExistingValuesForBlankFields() throws Exception {
            User target = fixtures.createUser();
            String originalName = target.getName();

            mockMvc.perform(post("/admin/user/{userId}", target.getId())
                            .param("name", "")
                            .param("email", "only-email@test.ono"))
                    .andExpect(status().is3xxRedirection());

            assertThat(userRepository.findById(target.getId()))
                    .get()
                    .satisfies(user -> {
                        assertThat(user.getName())
                                .as("빈 문자열로 이름을 날려버리면 운영 실수가 곧 데이터 손실이 된다")
                                .isEqualTo(originalName);
                        assertThat(user.getEmail()).isEqualTo("only-email@test.ono");
                    });
        }
    }

    @Nested
    @DisplayName("레벨 수동 조정")
    class UpdateUserLevel {

        @Test
        @DisplayName("능력치 종류별로 레벨과 포인트를 직접 설정할 수 있다")
        void setsLevelAndPointByAbilityType() throws Exception {
            User target = fixtures.createUser();

            mockMvc.perform(post("/admin/user/{userId}/level", target.getId())
                            .param("levelType", "attendance")
                            .param("levelValue", "7")
                            .param("pointValue", "42"))
                    .andExpect(status().isOk());

            assertThat(userRepository.findById(target.getId()))
                    .get()
                    .satisfies(user -> {
                        assertThat(user.getUserMissionStatus().getAttendanceLevel()).isEqualTo(7L);
                        assertThat(user.getUserMissionStatus().getAttendancePoint()).isEqualTo(42L);
                        assertThat(user.getUserMissionStatus().getNoteWriteLevel())
                                .as("지정하지 않은 능력치까지 건드리면 안 된다")
                                .isEqualTo(1L);
                    });
        }

        @ParameterizedTest(name = "levelType={0}")
        @ValueSource(strings = {"attendance", "noteWrite", "problemPractice", "notePractice", "totalStudy"})
        @DisplayName("지원하는 levelType 다섯 가지는 모두 성공한다")
        void acceptsEverySupportedLevelType(String levelType) throws Exception {
            User target = fixtures.createUser();

            mockMvc.perform(post("/admin/user/{userId}/level", target.getId())
                            .param("levelType", levelType)
                            .param("levelValue", "3")
                            .param("pointValue", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("알 수 없는 levelType 은 500이 아니라 404로 거절한다")
        void rejectsUnknownLevelType() throws Exception {
            User target = fixtures.createUser();

            mockMvc.perform(post("/admin/user/{userId}/level", target.getId())
                            .param("levelType", "unknown")
                            .param("levelValue", "3")
                            .param("pointValue", "1"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("levelValue 가 숫자가 아니면 400으로 거절한다")
        void rejectsNonNumericLevelValue() throws Exception {
            User target = fixtures.createUser();

            mockMvc.perform(post("/admin/user/{userId}/level", target.getId())
                            .param("levelType", "attendance")
                            .param("levelValue", "abc")
                            .param("pointValue", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("필수 파라미터가 빠지면 400으로 거절한다")
        void rejectsMissingParameters() throws Exception {
            User target = fixtures.createUser();

            mockMvc.perform(post("/admin/user/{userId}/level", target.getId())
                            .param("levelType", "attendance"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("사용자 삭제")
    class DeleteUser {

        @Test
        @DisplayName("관리자 화면에서는 사용자를 삭제할 수 없고 데이터가 그대로 남는다")
        void cannotDeleteUserFromAdmin() throws Exception {
            User target = fixtures.createUser();

            int status = mockMvc.perform(delete("/admin/user/{userId}", target.getId()))
                    .andReturn().getResponse().getStatus();

            assertThat(status)
                    .as("삭제 엔드포인트를 없앴으므로 성공 응답이 나가면 안 된다")
                    .isNotEqualTo(200);
            assertThat(userRepository.findById(target.getId())).isPresent();
        }
    }
}
