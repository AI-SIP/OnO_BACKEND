package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminUserResponseDto;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

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
    private List<AdminUserResponseDto> usersOf(MvcResult result) {
        return (List<AdminUserResponseDto>) result.getModelAndView().getModel().get("users");
    }

    @Nested
    @DisplayName("사용자 목록")
    class UserList {

        @Test
        @DisplayName("사용자가 하나도 없어도 500이 아니라 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenNoUserExists() throws Exception {
            // User 는 소프트 삭제라 deleteAll() 로는 행이 남는다. 집계가 0인 상황을 만들려면 물리 삭제해야 한다.
            jdbcTemplate.update("DELETE FROM `user`");

            mockMvc.perform(get("/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("users"))
                    .andExpect(model().attribute("totalUsers", 0L))
                    .andExpect(model().attribute("totalPages", 0))
                    .andExpect(model().attribute("pageStartItem", 0))
                    .andExpect(model().attribute("pageEndItem", 0))
                    .andExpect(model().attribute("hasPreviousBlock", false))
                    .andExpect(model().attribute("hasNextBlock", false));
        }

        @Test
        @DisplayName("전체 사용자를 페이지 단위로 끊어서 보여준다")
        void paginatesUsers() throws Exception {
            fixtures.createUser();
            fixtures.createUser();
            fixtures.createUser();

            MvcResult firstPage = mockMvc.perform(get("/admin/users").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalUsers", 4L))
                    .andExpect(model().attribute("totalPages", 2))
                    .andExpect(model().attribute("currentPage", 0))
                    .andExpect(model().attribute("pageStartItem", 1))
                    .andExpect(model().attribute("pageEndItem", 2))
                    .andReturn();

            assertThat(usersOf(firstPage))
                    .as("size=2 를 줬으면 한 페이지에 2명만 나와야 한다")
                    .hasSize(2);

            MvcResult secondPage = mockMvc.perform(get("/admin/users").param("page", "1").param("size", "2"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(usersOf(secondPage)).hasSize(2);
            assertThat(usersOf(secondPage))
                    .as("페이지가 겹치면 같은 사용자가 두 번 보인다")
                    .extracting(AdminUserResponseDto::userId)
                    .doesNotContainAnyElementsOf(usersOf(firstPage).stream().map(AdminUserResponseDto::userId).toList());
        }

        @Test
        @DisplayName("사용자마다 소유한 문제 수를 함께 집계한다")
        void aggregatesProblemCountPerUser() throws Exception {
            User target = fixtures.createUser();
            Folder folder = fixtures.createRootFolder(target.getId());
            saveProblem(target.getId(), folder, "문제1");
            saveProblem(target.getId(), folder, "문제2");

            MvcResult result = mockMvc.perform(get("/admin/users").param("size", "50"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(usersOf(result))
                    .filteredOn(dto -> dto.userId().equals(target.getId()))
                    .singleElement()
                    .satisfies(dto -> {
                        assertThat(dto.problemCount()).isEqualTo(2L);
                        assertThat(dto.totalStudyLevel()).as("신규 사용자는 레벨 1에서 시작한다").isEqualTo(1L);
                    });

            assertThat(usersOf(result))
                    .filteredOn(dto -> dto.userId().equals(admin.getId()))
                    .singleElement()
                    .satisfies(dto -> assertThat(dto.problemCount())
                            .as("문제가 없는 사용자는 null 이 아니라 0 이어야 한다")
                            .isEqualTo(0L));
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
        @DisplayName("size 가 전체 건수보다 커도 한 페이지에 전부 담아 준다")
        void allowsOversizedPageSize() throws Exception {
            fixtures.createUser();
            fixtures.createUser();

            MvcResult result = mockMvc.perform(get("/admin/users").param("size", "10000"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPages", 1))
                    .andReturn();

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
        @DisplayName("사용자의 문제·폴더·복습노트·미션 기록을 모두 모아 보여준다")
        void showsAllOwnedDataOfUser() throws Exception {
            User target = fixtures.createUser();
            Folder folder = fixtures.createRootFolder(target.getId());
            saveProblem(target.getId(), folder, "메모");
            savePracticeNote(target.getId(), "복습노트");
            saveMissionLog(target, MissionType.USER_LOGIN, null);

            mockMvc.perform(get("/admin/user/{userId}", target.getId()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user"))
                    .andExpect(model().attribute("problemCount", 1L))
                    .andExpect(model().attribute("folderCount", 1))
                    .andExpect(model().attribute("practiceNoteCount", 1))
                    .andExpect(model().attribute("missionLogCount", 1));
        }

        @Test
        @DisplayName("다른 사용자의 데이터는 섞이지 않는다")
        void doesNotMixDataOfOtherUsers() throws Exception {
            User target = fixtures.createUser();
            User other = fixtures.createOtherUser();
            Folder otherFolder = fixtures.createRootFolder(other.getId());
            saveProblem(other.getId(), otherFolder, "남의 문제");

            mockMvc.perform(get("/admin/user/{userId}", target.getId()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("problemCount", 0L))
                    .andExpect(model().attribute("folderCount", 0));
        }

        @Test
        @DisplayName("없는 사용자를 조회하면 500이 아니라 404로 응답한다")
        void returnsNotFoundForUnknownUser() throws Exception {
            mockMvc.perform(get("/admin/user/{userId}", 999_999L))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("사용자 수정")
    class UpdateUser {

        @Test
        @DisplayName("이름·이메일을 수정하면 저장하고 상세 화면으로 리다이렉트한다")
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
        @DisplayName("사용자를 삭제하면 200을 주고 조회되지 않는다")
        void deletesUser() throws Exception {
            User target = fixtures.createUser();

            mockMvc.perform(delete("/admin/user/{userId}", target.getId()))
                    .andExpect(status().isOk());

            assertThat(userRepository.findById(target.getId()))
                    .as("소프트 삭제 후에도 조회되면 탈퇴가 동작하지 않은 것이다")
                    .isEmpty();
        }

        @Test
        @DisplayName("삭제해도 다른 사용자는 남는다")
        void keepsOtherUsers() throws Exception {
            User target = fixtures.createUser();
            User other = fixtures.createOtherUser();

            mockMvc.perform(delete("/admin/user/{userId}", target.getId()))
                    .andExpect(status().isOk());

            assertThat(userRepository.findById(other.getId())).isPresent();
        }

        @Test
        @DisplayName("없는 사용자를 삭제하면 404로 응답한다")
        void returnsNotFoundForUnknownUser() throws Exception {
            mockMvc.perform(delete("/admin/user/{userId}", 999_999L))
                    .andExpect(status().isNotFound());
        }
    }
}
