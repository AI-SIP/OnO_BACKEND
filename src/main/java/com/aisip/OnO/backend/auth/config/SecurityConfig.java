package com.aisip.OnO.backend.auth.config;

import com.aisip.OnO.backend.admin.service.CustomAdminService;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.service.JwtTokenizer;
import com.aisip.OnO.backend.common.auth.CustomAccessDeniedHandler;
import com.aisip.OnO.backend.common.auth.CustomAuthenticationEntryPoint;
import com.aisip.OnO.backend.common.auth.JwtTokenFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

    @Value("${spring.site.url}")
    private String siteUrl;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        http.csrf(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers("/", "/robots.txt", "/home","/images/**", "/login", "/css/**", "/js/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/actuator/health").permitAll()
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .requestMatchers("/api/fcm/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/users/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/problems/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/folders/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/fileUpload/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .requestMatchers("/api/practiceNotes/**").hasAnyRole("GUEST", "MEMBER", "ADMIN")
                                .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login") // 커스텀 로그인 페이지 경로
                        .loginProcessingUrl("/perform_login")
                        .defaultSuccessUrl("/admin/main", true)
                        .successHandler((request, response, authentication) -> {
                            CustomAdminService userDetails = (CustomAdminService) authentication.getPrincipal();
                            Long adminId = userDetails.getUserId();
                            String token = jwtTokenizer.createAccessToken(String.valueOf(adminId), Map.of("authority", Authority.ROLE_ADMIN));
                            response.setHeader("Authorization", "Bearer " + token);
                            response.sendRedirect(siteUrl + "/admin/main"); // 성공 후 관리자 페이지로 이동
                        })
                        .failureHandler((request, response, exception) -> {
                             response.sendRedirect(siteUrl + "/login?error");
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl(siteUrl + "/logout")
                        .logoutSuccessUrl(siteUrl + "/login?logout")
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
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // ✅ 허용할 HTTP 메서드
        config.setAllowedHeaders(List.of("*")); // ✅ 모든 헤더 허용
        config.setAllowCredentials(true); // ✅ 인증 정보 포함 요청 허용 (JWT 포함)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}