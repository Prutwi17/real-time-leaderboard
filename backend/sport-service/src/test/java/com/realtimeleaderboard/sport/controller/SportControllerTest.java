package com.realtimeleaderboard.sport.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realtimeleaderboard.sport.config.SecurityConfig;
import com.realtimeleaderboard.sport.dto.response.SportResponse;
import com.realtimeleaderboard.sport.exception.ConflictException;
import com.realtimeleaderboard.sport.exception.DuplicateResourceException;
import com.realtimeleaderboard.sport.exception.ResourceNotFoundException;
import com.realtimeleaderboard.sport.security.JwtAuthenticationFilter;
import com.realtimeleaderboard.sport.security.JwtService;
import com.realtimeleaderboard.sport.service.CompetitionService;
import com.realtimeleaderboard.sport.service.SportService;
import com.realtimeleaderboard.sport.support.TestJwtFactory;
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

@WebMvcTest(SportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class SportControllerTest {

    private static final String BEARER = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    private static final SportService sportService = Mockito.mock(SportService.class);
    private static final CompetitionService competitionService = Mockito.mock(CompetitionService.class);

    @BeforeEach
    void resetMocks() {
        Mockito.reset(sportService, competitionService);
    }

    @TestConfiguration
    static class ServiceMocks {
        @Bean
        SportService sportService() {
            return sportService;
        }

        @Bean
        CompetitionService competitionService() {
            return competitionService;
        }
    }

    private String adminToken() {
        return TestJwtFactory.adminToken(9L, "admin_user");
    }

    private String userToken() {
        return TestJwtFactory.userToken(1L, "plain_user");
    }

    @Test
    void getAllIsPublic() throws Exception {
        when(sportService.getAll()).thenReturn(List.of(
                new SportResponse(1L, "FOOTBALL", "Football", "d", true),
                new SportResponse(2L, "CRICKET", "Cricket", "d", true),
                new SportResponse(3L, "F1", "Formula 1", "d", true)));

        mockMvc.perform(get("/api/sports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("FOOTBALL"))
                .andExpect(jsonPath("$[2].name").value("Formula 1"));
    }

    @Test
    void getByIdIsPublic() throws Exception {
        when(sportService.getById(1L)).thenReturn(new SportResponse(1L, "FOOTBALL", "Football", "d", true));

        mockMvc.perform(get("/api/sports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getByIdNotFoundMapsTo404() throws Exception {
        when(sportService.getById(99L)).thenThrow(new ResourceNotFoundException("Sport not found: id 99"));

        mockMvc.perform(get("/api/sports/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getByCodeIsPublicAndValidatesEnum() throws Exception {
        when(sportService.getByCode(com.realtimeleaderboard.sport.entity.SportCode.FOOTBALL))
                .thenReturn(new SportResponse(1L, "FOOTBALL", "Football", "d", true));

        mockMvc.perform(get("/api/sports/code/FOOTBALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FOOTBALL"));

        // Unsupported sport code in the path is a client error, never a silent create.
        mockMvc.perform(get("/api/sports/code/BASKETBALL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/sports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"F1\",\"name\":\"Formula 1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void createRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/sports")
                        .header("Authorization", BEARER + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"F1\",\"name\":\"Formula 1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void createAsAdminReturns201() throws Exception {
        when(sportService.create(any()))
                .thenReturn(new SportResponse(3L, "F1", "Formula 1", null, true));

        mockMvc.perform(post("/api/sports")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"F1\",\"name\":\"Formula 1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        when(sportService.create(any()))
                .thenThrow(new DuplicateResourceException("Sport code 'F1' already exists"));

        mockMvc.perform(post("/api/sports")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"F1\",\"name\":\"Formula 1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void createWithUnknownSportCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/sports")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TENNIS\",\"name\":\"Tennis\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/sports")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"F1\",\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void updateRequiresAdmin() throws Exception {
        when(sportService.update(eq(1L), any())).thenReturn(new SportResponse(1L, "FOOTBALL", "Football", "d", true));

        mockMvc.perform(put("/api/sports/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Football\",\"description\":\"d\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/sports/1")
                        .header("Authorization", BEARER + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Football\",\"description\":\"d\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/sports/1")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Football\",\"description\":\"d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Football"));
    }

    @Test
    void updateStatusRequiresAdmin() throws Exception {
        when(sportService.updateStatus(eq(1L), any()))
                .thenReturn(new SportResponse(1L, "FOOTBALL", "Football", "d", false));

        mockMvc.perform(patch("/api/sports/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/sports/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/sports/1/status")
                        .header("Authorization", BEARER + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/sports/1/status")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteBlockedWithCompetitionsMapsTo409() throws Exception {
        doThrow(new ConflictException("Sport 'FOOTBALL' still has competitions; deactivate it instead of deleting"))
                .when(sportService).delete(1L);

        mockMvc.perform(delete("/api/sports/1")
                        .header("Authorization", BEARER + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Sport 'FOOTBALL' still has competitions; deactivate it instead of deleting"));
    }

    @Test
    void deleteAsAdminSucceedsWhenSafe() throws Exception {
        mockMvc.perform(delete("/api/sports/5")
                        .header("Authorization", BEARER + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sport deleted"));
    }

    @Test
    void nestedCompetitionEndpointsFollowSameRules() throws Exception {
        when(competitionService.getBySportId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/sports/1/competitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/sports/1/competitions")
                        .header("Authorization", BEARER + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Premier League\",\"code\":\"PREMIER_LEAGUE\"}"))
                .andExpect(status().isForbidden());
    }
}
