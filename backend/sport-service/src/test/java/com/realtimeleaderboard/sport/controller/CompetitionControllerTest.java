package com.realtimeleaderboard.sport.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realtimeleaderboard.sport.config.SecurityConfig;
import com.realtimeleaderboard.sport.dto.response.CompetitionResponse;
import com.realtimeleaderboard.sport.exception.ResourceNotFoundException;
import com.realtimeleaderboard.sport.security.JwtAuthenticationFilter;
import com.realtimeleaderboard.sport.security.JwtService;
import com.realtimeleaderboard.sport.service.CompetitionService;
import com.realtimeleaderboard.sport.support.TestJwtFactory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CompetitionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class CompetitionControllerTest {

    private static final String BEARER = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    private static final CompetitionService competitionService = Mockito.mock(CompetitionService.class);

    @BeforeEach
    void resetMocks() {
        Mockito.reset(competitionService);
    }

    @TestConfiguration
    static class ServiceMocks {
        @Bean
        CompetitionService competitionService() {
            return competitionService;
        }
    }

    private CompetitionResponse sample() {
        return new CompetitionResponse(1L, "Premier League", "PREMIER_LEAGUE", 1L, "FOOTBALL",
                "English top flight", true, LocalDate.of(2026, 8, 15), LocalDate.of(2027, 5, 24));
    }

    @Test
    void getAllIsPublic() throws Exception {
        when(competitionService.getAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/competitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sportCode").value("FOOTBALL"))
                .andExpect(jsonPath("$[0].code").value("PREMIER_LEAGUE"));
    }

    @Test
    void getByIdIsPublic() throws Exception {
        when(competitionService.getById(1L)).thenReturn(sample());

        mockMvc.perform(get("/api/competitions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Premier League"));
    }

    @Test
    void getByIdNotFoundMapsTo404() throws Exception {
        when(competitionService.getById(42L)).thenThrow(new ResourceNotFoundException("Competition not found: id 42"));

        mockMvc.perform(get("/api/competitions/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateRequiresAdmin() throws Exception {
        when(competitionService.update(eq(1L), any())).thenReturn(sample());

        mockMvc.perform(put("/api/competitions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Premier League\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/competitions/1")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(1L, "plain_user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Premier League\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/competitions/1")
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin_user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Premier League\",\"description\":\"English top flight\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatusRequiresAdmin() throws Exception {
        when(competitionService.updateStatus(eq(1L), any())).thenReturn(sample());

        mockMvc.perform(patch("/api/competitions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/competitions/1/status")
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin_user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRequiresAdmin() throws Exception {
        mockMvc.perform(delete("/api/competitions/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/competitions/1")
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin_user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Competition deleted"));
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        // endDate before startDate on update.
        mockMvc.perform(put("/api/competitions/1")
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin_user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Dates League\","
                                + "\"startDate\":\"2027-01-01\",\"endDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        // Missing name on update.
        mockMvc.perform(put("/api/competitions/1")
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin_user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
