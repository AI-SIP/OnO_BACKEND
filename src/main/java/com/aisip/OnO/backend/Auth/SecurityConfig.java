package com.aisip.OnO.backend.Auth;

import com.aisip.OnO.backend.service.CustomAdminService;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.jwt.secret}")
    private String secret;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable()
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers("/", "/home","/images/**", "/api/auth/**", "/login", "/css/**", "/js/**").permitAll()
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
                            response.sendRedirect("/admin/main"); // 성공 후 관리자 페이지로 이동
                        })
                        .failureHandler((request, response, exception) -> {
                            response.sendRedirect("/login?error");
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
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