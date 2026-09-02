package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminProblemResponseDto;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.folder.dto.FolderResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.problem.dto.ProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminProblemController")
class AdminProblemControllerTest extends AdminTestSupport {

    private User owner;
    private Folder folder;

    @BeforeEach
    void setUp() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
        owner = fixtures.createUser();
        folder = fixtures.createRootFolder(owner.getId());
    }

    @SuppressWarnings("unchecked")
    private List<AdminProblemResponseDto> problemsOf(MvcResult result) {
        return (List<AdminProblemResponseDto>) result.getModelAndView().getModel().get("problems");
    }

    @Nested
    @DisplayName("문제 목록")
    class ProblemList {

        @Test
        @DisplayName("문제가 하나도 없어도 0 기반 집계를 돌려준다")
        void rendersZeroBasedResultWhenNoProblemExists() throws Exception {
            mockMvc.perform(get("/admin/problems"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("problems"))
                    .andExpect(model().attribute("totalProblems", 0L))
                    .andExpect(model().attribute("totalPages", 0))
                    .andExpect(model().attribute("pageStartItem", 0))
                    .andExpect(model().attribute("pageEndItem", 0));
        }

        @Test
        @DisplayName("등록된 문제를 페이지 단위로 보여준다")
        void paginatesProblems() throws Exception {
            saveProblem(owner.getId(), folder, "문제1");
            saveProblem(owner.getId(), folder, "문제2");
            saveProblem(owner.getId(), folder, "문제3");

            MvcResult result = mockMvc.perform(get("/admin/problems").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalProblems", 3L))
                    .andExpect(model().attribute("totalPages", 2))
                    .andReturn();

            assertThat(problemsOf(result)).hasSize(2);
        }

        @Test
        @DisplayName("다른 사용자의 문제도 함께 보인다 - 관리자 화면은 전체 조회다")
        void showsProblemsOfEveryUser() throws Exception {
            User other = fixtures.createOtherUser();
            Folder otherFolder = fixtures.createRootFolder(other.getId());
            saveProblem(owner.getId(), folder, "내 문제");
            saveProblem(other.getId(), otherFolder, "남의 문제");

            MvcResult result = mockMvc.perform(get("/admin/problems").param("size", "50"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(problemsOf(result))
                    .as("전체 조회이므로 관리자에게는 모든 사용자의 문제가 보여야 한다")
                    .hasSize(2);
        }

        @ParameterizedTest(name = "page={0}")
        @ValueSource(ints = {-1, -50})
        @DisplayName("음수 page 는 0페이지로 보정한다")
        void clampsNegativePage(int page) throws Exception {
            saveProblem(owner.getId(), folder, "문제");

            mockMvc.perform(get("/admin/problems").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentPage", 0));
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("0 이하 size 는 1로 보정한다")
        void clampsNonPositiveSize(int size) throws Exception {
            saveProblem(owner.getId(), folder, "문제");

            mockMvc.perform(get("/admin/problems").param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("size", 1));
        }

        @Test
        @DisplayName("마지막 페이지를 넘는 page 는 마지막 페이지로 되돌려 빈 화면을 보여주지 않는다")
        void clampsPageBeyondLastPage() throws Exception {
            saveProblem(owner.getId(), folder, "문제1");
            saveProblem(owner.getId(), folder, "문제2");

            MvcResult result = mockMvc.perform(get("/admin/problems").param("page", "99").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentPage", 1))
                    .andReturn();

            assertThat(problemsOf(result))
                    .as("범위를 벗어난 page 요청이 빈 목록이 되면 관리자가 데이터가 사라진 줄 안다")
                    .hasSize(1);
        }

        @Test
        @DisplayName("size 가 전체 건수보다 커도 한 페이지로 묶어 준다")
        void allowsOversizedPageSize() throws Exception {
            saveProblem(owner.getId(), folder, "문제");

            mockMvc.perform(get("/admin/problems").param("size", "10000"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("totalPages", 1));
        }
    }

    @Nested
    @DisplayName("문제 상세")
    class ProblemDetail {

        @Test
        @DisplayName("문제와 함께 폴더·작성자·풀이 기록을 모아 보여준다")
        void showsProblemWithFolderAndOwner() throws Exception {
            Problem problem = saveProblem(owner.getId(), folder, "삼각함수 실수");

            MvcResult result = mockMvc.perform(get("/admin/problem/{problemId}", problem.getId()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("problem"))
                    .andExpect(model().attribute("problemSolveCount", 0))
                    .andReturn();

            ProblemResponseDto problemDto = (ProblemResponseDto) result.getModelAndView().getModel().get("problem");
            FolderResponseDto folderDto = (FolderResponseDto) result.getModelAndView().getModel().get("folder");
            UserResponseDto userDto = (UserResponseDto) result.getModelAndView().getModel().get("user");

            assertThat(problemDto.problemId()).isEqualTo(problem.getId());
            assertThat(folderDto.folderId()).isEqualTo(folder.getId());
            assertThat(userDto.userId())
                    .as("작성자를 잘못 짚으면 관리자가 엉뚱한 사용자를 제재하게 된다")
                    .isEqualTo(owner.getId());
        }

        @Test
        @DisplayName("없는 문제를 조회하면 500이 아니라 404로 응답한다")
        void returnsNotFoundForUnknownProblem() throws Exception {
            mockMvc.perform(get("/admin/problem/{problemId}", 999_999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("problemId 가 숫자가 아니면 400으로 거절한다")
        void rejectsNonNumericProblemId() throws Exception {
            mockMvc.perform(get("/admin/problem/{problemId}", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }
}
