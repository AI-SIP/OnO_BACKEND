package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminStatsDto;
import com.aisip.OnO.backend.admin.service.AdminStatsService;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminController")
class AdminControllerTest extends AdminTestSupport {

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    @BeforeEach
    void loginAsAdmin() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
    }

    @Test
    @DisplayName("데이터가 없어도 관리자 홈을 렌더링한다")
    void rendersAdminMainPage() throws Exception {
        mockMvc.perform(get("/admin/main"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(model().attributeExists("home", "today"));
    }

    @Test
    @DisplayName("홈은 오늘 등록한 오답노트와 복습을 어제와 비교하고 최근 목록을 보여 준다")
    void showsTodayAgainstYesterday() throws Exception {
        LocalDate today = LocalDate.now();
        User user = fixtures.createUser();
        Problem todayProblem = saveProblem(user.getId(), null, "오늘");
        Problem yesterdayProblem = saveProblem(user.getId(), null, "어제");
        forceCreatedAt("problem", yesterdayProblem.getId(), today.minusDays(1).atTime(9, 0));
        Problem another = saveProblem(user.getId(), null, "오늘 또");
        problemSolveRepository.save(ProblemSolve.create(
                todayProblem, user.getId(), LocalDateTime.now(), AnswerStatus.CORRECT, null, null, null, null));
        saveMissionLog(user, MissionType.USER_LOGIN, null);

        MvcResult result = mockMvc.perform(get("/admin/main"))
                .andExpect(status().isOk())
                .andReturn();

        AdminStatsService.Home home = (AdminStatsService.Home) result.getModelAndView().getModel().get("home");
        assertThat(home.problems().value()).isEqualTo(2.0);
        assertThat(home.problems().previous()).isEqualTo(1.0);
        assertThat(home.solves().value()).isEqualTo(1.0);
        assertThat(home.activeUsers().value()).isEqualTo(1.0);
        assertThat(home.recentDau()).hasSize(14);
        assertThat(home.recentProblems()).extracting(AdminStatsDto.RecentProblem::problemId)
                .as("최근 오답노트는 등록 시각이 늦은 것부터 나온다")
                .startsWith(another.getId());
        assertThat(home.recentUsers()).extracting(AdminStatsDto.RecentUser::userId).contains(user.getId());
    }

    @Test
    @DisplayName("이미지 뷰어는 넘겨받은 url 을 그대로 모델에 담는다")
    void putsImageUrlIntoModel() throws Exception {
        mockMvc.perform(get("/admin/user/image/view").param("url", "https://cdn.test.ono/problem/1.png"))
                .andExpect(status().isOk())
                .andExpect(view().name("image"))
                .andExpect(model().attribute("imageUrl", "https://cdn.test.ono/problem/1.png"));
    }

    @Test
    @DisplayName("url 파라미터가 없으면 400으로 거절한다")
    void rejectsMissingUrlParameter() throws Exception {
        mockMvc.perform(get("/admin/user/image/view"))
                .andExpect(status().isBadRequest());
    }
}
