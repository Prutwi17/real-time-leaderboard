package com.realtimeleaderboard.sport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeleaderboard.sport.dto.response.ErrorResponse;
import com.realtimeleaderboard.sport.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Public reads, ADMIN-only management. Authentication is inherited from the
 * existing JWT architecture (auth-service issues; this service validates).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Public read endpoints
                        .requestMatchers(HttpMethod.GET,
                                "/api/sports/**", "/api/competitions/**").permitAll()
                        // Management endpoints require ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/sports/**", "/api/competitions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/sports/**", "/api/competitions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/sports/**", "/api/competitions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/sports/**", "/api/competitions/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(this::unauthorized)
                        .accessDeniedHandler(this::forbidden))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void unauthorized(HttpServletRequest request, HttpServletResponse response,
                              org.springframework.security.core.AuthenticationException ex) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication required", request.getRequestURI());
    }

    private void forbidden(HttpServletRequest request, HttpServletResponse response,
                           org.springframework.security.access.AccessDeniedException ex) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "Insufficient permissions", request.getRequestURI());
    }

    private void writeError(HttpServletResponse response, int status, String error, String message, String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new ErrorResponse(status, error, message, path, List.of()));
    }
}
