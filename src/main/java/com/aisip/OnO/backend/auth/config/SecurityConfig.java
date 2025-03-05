package com.aisip.OnO.backend.auth.config;

import com.aisip.OnO.backend.admin.service.CustomAdminService;
import com.aisip.OnO.backend.auth.filter.JwtTokenFilter;
import com.aisip.OnO.backend.auth.service.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.jwt.secret}")
    private String secret;

    @Value("${spring.site.url}")  // 애플리케이션의 HTTPS 기본 URL을 환경 변수로 받아옴
    private String siteUrl;

    @Bean
    public JwtTokenFilter jwtTokenFilter() {
        return new JwtTokenFilter(secret);
    }

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
        http.exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint())
                .and()
                .cors().and().csrf().disable()
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers("/", "/robots.txt", "/home","/images/**", "/api/auth/**", "/login", "/css/**", "/js/**").permitAll()
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login") // 커스텀 로그인 페이지 경로
                        .loginProcessingUrl("/perform_login")
                        .defaultSuccessUrl("/admin/main", true)
                        .successHandler((request, response, authentication) -> {
                            CustomAdminService userDetails = (CustomAdminService) authentication.getPrincipal();
                            Long adminId = userDetails.getUserId();
                            String token = jwtTokenProvider.createAccessToken(adminId);
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
                //.addFilterBefore(new IpLoggingFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}