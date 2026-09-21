package com.aisip.OnO.backend.auth.config;

import com.aisip.OnO.backend.admin.service.CustomAdminService;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.service.JwtTokenizer;
import com.aisip.OnO.backend.common.auth.CustomAccessDeniedHandler;
import com.aisip.OnO.backend.common.auth.CustomAuthenticationEntryPoint;
import com.aisip.OnO.backend.common.auth.JwtTokenFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 관리자 로그인 유지 시간. 세션 유휴 만료와 로그인 유지 쿠키 수명을 같은 값으로 맞춘다. */
    private static final int ADMIN_LOGIN_TTL_SECONDS = (int) java.time.Duration.ofHours(24).toSeconds();

    private static final String ADMIN_REMEMBER_ME_COOKIE = "ONO_ADMIN_REMEMBER";

    @Value("${spring.site.url}")
    private String siteUrl;

    /**
     * 로그인 유지 쿠키의 서명 키. 재시작해도 같아야 배포 뒤에 로그인이 풀리지 않으므로
     * 설정 파일에 이미 있는 비밀값에서 만든다. 액세스 토큰 서명 키와 섞이지 않게 접두사를 붙인다.
     */
    @Value("${jwt.accessToken.secret}")
    private String accessTokenSecret;

    public final JwtTokenFilter jwtTokenFilter;

    private final JwtTokenizer jwtTokenizer;

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> response.sendRedirect(siteUrl+ "/home");
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers("/actuator/**");
    }

    @Bean
    @Order(0)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        http.csrf(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                                .requestMatchers(request -> request.getRequestURI() != null && request.getRequestURI().contains("/actuator/")).permitAll()
                                .requestMatchers(
                                        "/",
                                        "/robots.txt",
                                        "/home",
                                        "/images/**",
                                        "/login",
                                        "/css/**",
                                        "/js/**",
                                        "/swagger-ui/**",
                                        "/v3/api-docs/**",
                                        "/actuator/**",
                                        "/grafana",
                                        "/grafana/**",
                                        "/prometheus",
                                        "/prometheus/**",
                                        "/feedback",
                                        "/feedback/**"
                                ).permitAll()
                                .requestMatchers("/api/auth/logout").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .requestMatchers("/api/fcm/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/users/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/problems/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/folders/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/fileUpload/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/practiceNotes/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/notices/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/study-room/**", "/api/study-rooms/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login") // 커스텀 로그인 페이지 경로
                        .loginProcessingUrl("/perform_login")
                        .defaultSuccessUrl("/admin/main", true)
                        .successHandler((request, response, authentication) -> {
                            CustomAdminService userDetails = (CustomAdminService) authentication.getPrincipal();
                            Long adminId = userDetails.getUserId();
                            // createAccessToken 은 이미 "Bearer " 접두사를 포함해 반환한다
                            // (JwtTokenizer.BEARER_PREFIX). 여기서 한 번 더 붙이면
                            // "Bearer Bearer eyJ..." 가 되어 JwtTokenFilter 가 앞 7글자만 떼고
                            // 파싱에 실패해 401 이 난다. 프론트에 내려가는 토큰 형식이 이미
                            // 접두사를 포함한 계약이므로 createAccessToken 쪽은 그대로 둔다.
                            String token = jwtTokenizer.createAccessToken(String.valueOf(adminId), Map.of("authority", Authority.ROLE_ADMIN));
                            response.setHeader("Authorization", token);
                            // 톰캣 기본 세션 유휴 만료는 30분이라 화면을 잠깐 켜 두기만 해도 로그인이 풀렸다.
                            request.getSession().setMaxInactiveInterval(ADMIN_LOGIN_TTL_SECONDS);
                            response.sendRedirect(siteUrl + "/admin/main"); // 성공 후 관리자 페이지로 이동
                        })
                        .failureHandler((request, response, exception) -> {
                             response.sendRedirect(siteUrl + "/login?error");
                        })
                        .permitAll()
                )
                // 세션은 메모리에만 있어서 배포로 컨테이너가 바뀌면 사라진다.
                // 로그인 유지 쿠키가 있으면 새 컨테이너에서도 다시 로그인하지 않고 이어서 쓸 수 있다.
                .rememberMe(rememberMe -> rememberMe
                        .key("ono-admin-remember-me:" + accessTokenSecret)
                        .rememberMeCookieName(ADMIN_REMEMBER_ME_COOKIE)
                        .tokenValiditySeconds(ADMIN_LOGIN_TTL_SECONDS)
                        .alwaysRemember(true)
                        .useSecureCookie(siteUrl.startsWith("https"))
                )
                .logout(logout -> logout
                        // logoutUrl 은 요청 경로와 비교하는 값이다. 전체 URL 을 넣으면 어떤 요청과도
                        // 맞지 않아서 로그아웃 버튼을 눌러도 세션이 그대로 남았다.
                        .logoutUrl("/logout")
                        .deleteCookies("JSESSIONID", ADMIN_REMEMBER_ME_COOKIE)
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.sendRedirect(siteUrl + "/login?logout"))
                        .permitAll()
                )
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) // 세션을 필요할 때만 생성
                )
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);

        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
        );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of("*")); // ✅ 모든 도메인에서 접근 가능
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")); // ✅ 허용할 HTTP 메서드
        config.setAllowedHeaders(List.of("*")); // ✅ 모든 헤더 허용
        config.setAllowCredentials(true); // ✅ 인증 정보 포함 요청 허용 (JWT 포함)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
