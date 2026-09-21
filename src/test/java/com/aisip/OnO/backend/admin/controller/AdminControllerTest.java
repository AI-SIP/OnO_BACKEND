package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminController")
class AdminControllerTest extends AdminTestSupport {

    @BeforeEach
    void loginAsAdmin() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
    }

    @Test
    @DisplayName("관리자 메인 화면을 렌더링한다")
    void rendersAdminMainPage() throws Exception {
        mockMvc.perform(get("/admin/main"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"));
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
