package com.realtimeleaderboard.sport.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realtimeleaderboard.sport.config.DefaultSportsInitializer;
import com.realtimeleaderboard.sport.repository.CompetitionRepository;
import com.realtimeleaderboard.sport.repository.SportRepository;
import com.realtimeleaderboard.sport.support.TestJwtFactory;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SportServiceIntegrationTest {

    private static final String BEARER = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SportRepository sportRepository;

    @Autowired
    private CompetitionRepository competitionRepository;

    private String adminToken() {
        return TestJwtFactory.adminToken(9L, "admin_user");
    }

    private String userToken() {
        return TestJwtFactory.userToken(1L, "plain_user");
    }

    @Test
    void contextSeedsExactlyThreeDefaultSports() {
        assertThat(sportRepository.count()).isEqualTo(3);
        assertThat(sportRepository.findAll())
                .extracting(s -> s.getCode().name())
                .containsExactlyInAnyOrder("FOOTBALL", "CRICKET", "F1");
    }

    @Test
    void initializerIsIdempotentOnRestart() throws Exception {
        long before = sportRepository.count();

        new DefaultSportsInitializer(sportRepository).run();

        assertThat(sportRepository.count()).isEqualTo(before);
    }

    @Test
    void publicReadEndpointsRequireNoAuthentication() throws Exception {
        mockMvc.perform(get("/api/sports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/sports/code/FOOTBALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FOOTBALL"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unsupportedSportCodeIsRejectedOnLookup() throws Exception {
        mockMvc.perform(get("/api/sports/code/BASKETBALL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createSportEnforcesAuthenticationAndUniqueness() throws Exception {
        String body = "{\"code\":\"F1\",\"name\":\"Formula 1\",\"description\":\"Motor racing\"}";

        mockMvc.perform(post("/api/sports").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sports")
                        .header("Authorization", BEARER + userToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        // F1 already exists from seeding -> duplicate must 409 even for ADMIN.
        mockMvc.perform(post("/api/sports")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"F1\",\"name\":\"Formula One\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void updateAndStatusFlowForSport() throws Exception {
        Long f1Id = sportRepository.findByCode(com.realtimeleaderboard.sport.entity.SportCode.F1).orElseThrow().getId();

        mockMvc.perform(put("/api/sports/" + f1Id)
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Formula 1\",\"description\":\"FIA Formula One World Championship\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("FIA Formula One World Championship"));

        mockMvc.perform(patch("/api/sports/" + f1Id + "/status")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // The record is still listable; deactivation is not deletion.
        assertThat(sportRepository.findById(f1Id).orElseThrow().isActive()).isFalse();
        mockMvc.perform(get("/api/sports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void competitionLifecycleThroughNestedRoutes() throws Exception {
        Long footballId = sportRepository.findByCode(
                com.realtimeleaderboard.sport.entity.SportCode.FOOTBALL).orElseThrow().getId();
        String createBody = "{\"name\":\"Premier League\",\"code\":\"PREMIER_LEAGUE\","
                + "\"description\":\"English top flight\","
                + "\"startDate\":\"2026-08-15\",\"endDate\":\"2027-05-24\"}";

        MvcResult created = mockMvc.perform(post("/api/sports/" + footballId + "/competitions")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sportId").value(footballId.intValue()))
                .andExpect(jsonPath("$.sportCode").value("FOOTBALL"))
                .andExpect(jsonPath("$.code").value("PREMIER_LEAGUE"))
                .andReturn();
        String responseBody = created.getResponse().getContentAsString();
        long competitionId = com.jayway.jsonpath.JsonPath.parse(responseBody).read("$.id", Long.class);

        // Duplicate code is rejected.
        mockMvc.perform(post("/api/sports/" + footballId + "/competitions")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Premier League\",\"code\":\"PREMIER_LEAGUE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));

        // Competitions for the sport are publicly readable.
        mockMvc.perform(get("/api/sports/" + footballId + "/competitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Premier League"));

        mockMvc.perform(get("/api/competitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/competitions/" + competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value("2027-05-24"));

        mockMvc.perform(put("/api/competitions/" + competitionId)
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Premier League\",\"description\":\"Updated description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description"));

        mockMvc.perform(patch("/api/competitions/" + competitionId + "/status")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/competitions/" + competitionId)
                        .header("Authorization", BEARER + adminToken()))
                .andExpect(status().isOk());

        assertThat(competitionRepository.count()).isZero();
    }

    @Test
    void invalidCompetitionRequestsAreRejected() throws Exception {
        Long cricketId = sportRepository.findByCode(
                com.realtimeleaderboard.sport.entity.SportCode.CRICKET).orElseThrow().getId();

        // Unknown target sport.
        mockMvc.perform(post("/api/sports/999/competitions")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost League\",\"code\":\"GHOST\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

        // endDate before startDate.
        mockMvc.perform(post("/api/sports/" + cricketId + "/competitions")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Dates Cup\",\"code\":\"BAD_DATES\","
                                + "\"startDate\":\"2027-01-01\",\"endDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest());

        // Missing name.
        mockMvc.perform(post("/api/sports/" + cricketId + "/competitions")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NO_NAME\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sportDeletionIsBlockedWhileCompetitionsExistThenSucceedsAfterCleanup() throws Exception {
        Long cricketId = sportRepository.findByCode(
                com.realtimeleaderboard.sport.entity.SportCode.CRICKET).orElseThrow().getId();

        mockMvc.perform(post("/api/sports/" + cricketId + "/competitions")
                        .header("Authorization", BEARER + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ICC World Cup\",\"code\":\"ICC_WORLD_CUP\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/sports/" + cricketId)
                        .header("Authorization", BEARER + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        mockMvc.perform(delete("/api/competitions/"
                        + competitionRepository.findAllWithSportBySportId(cricketId).get(0).getId())
                        .header("Authorization", BEARER + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/sports/" + cricketId)
                        .header("Authorization", BEARER + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sports/code/CRICKET"))
                .andExpect(status().isNotFound());
    }

    @Test
    void managementOperationsRejectMissingOrNonAdminTokensAcrossBothResources() throws Exception {
        String competitionBody = "{\"name\":\"La Liga\",\"code\":\"LA_LIGA\"}";
        Long footballId = sportRepository.findByCode(
                com.realtimeleaderboard.sport.entity.SportCode.FOOTBALL).orElseThrow().getId();

        mockMvc.perform(put("/api/sports/" + footballId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/sports/" + footballId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/sports/" + footballId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/competitions/123"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sports/" + footballId + "/competitions")
                        .header("Authorization", BEARER + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(competitionBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/competitions/123")
                        .header("Authorization", BEARER + userToken()))
                .andExpect(status().isForbidden());

        // Garbage tokens are equivalent to unauthenticated.
        mockMvc.perform(get("/api/sports")
                        .header("Authorization", BEARER + "not-a-real-token"))
                .andExpect(status().isOk()); // public read unaffected

        mockMvc.perform(post("/api/sports")
                        .header("Authorization", BEARER + "not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CRICKET\",\"name\":\"Cricket\"}"))
                .andExpect(status().isUnauthorized());
    }
}
