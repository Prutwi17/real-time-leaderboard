package com.realtimeleaderboard.score.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.realtimeleaderboard.score.KafkaTestConfig;
import com.realtimeleaderboard.score.client.SportServiceClient;
import com.realtimeleaderboard.score.client.SportSnapshot;
import com.realtimeleaderboard.score.exception.ConflictException;
import com.realtimeleaderboard.score.exception.ResourceNotFoundException;
import com.realtimeleaderboard.score.exception.ServiceUnavailableException;
import com.realtimeleaderboard.score.kafka.ScoreEventPublisher;
import com.realtimeleaderboard.score.support.TestJwtFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(KafkaTestConfig.class)
class ScoreServiceIntegrationTest {

    private static final String BEARER = "Bearer ";
    @Autowired private MockMvc mockMvc;
    @Autowired private ScoreEventPublisher scoreEventPublisher;

    private static final SportServiceClient sportClient = Mockito.mock(SportServiceClient.class);

    @TestConfiguration
    static class MockClients {
        @Bean @Primary SportServiceClient sportServiceClient() { return sportClient; }
    }

    @BeforeEach
    void stubClients() {
        Mockito.reset(sportClient);
        Mockito.reset(scoreEventPublisher);
        when(sportClient.fetchSport(1L)).thenReturn(new SportSnapshot(1L, "FOOTBALL", "Football", true));
        when(sportClient.fetchSport(2L)).thenReturn(new SportSnapshot(2L, "CRICKET", "Cricket", true));
        when(sportClient.fetchSport(3L)).thenReturn(new SportSnapshot(3L, "F1", "Formula 1", true));
    }

    private static String footballBody(String eventId, String subId) {
        String id = subId != null ? ",\"submissionId\":\"" + subId + "\"" : "";
        String ev  = eventId != null ? ",\"eventId\":\"" + eventId + "\"" : "";
        return "{\"sportId\":1,\"value\":100,\"eventName\":\"EPL Match\"" + ev + ",\"scoreType\":\"GOALS\"" + id + "}";
    }

    private static String cricketBody(String subId) {
        String id = subId != null ? ",\"submissionId\":\"" + subId + "\"" : "";
        return "{\"sportId\":2,\"value\":50,\"scoreType\":\"RUNS\"" + id + "}";
    }

    private static String f1Body(String subId) {
        String id = subId != null ? ",\"submissionId\":\"" + subId + "\"" : "";
        return "{\"sportId\":3,\"value\":85.12,\"scoreType\":\"LAP_TIME\"" + id + "}";
    }

    @Test
    void fullFlow_submitAndRetrieve() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(200L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(footballBody("EPL-001", "flow-sub-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(200))
                .andExpect(jsonPath("$.sportId").value(1))
                .andExpect(jsonPath("$.value").value(100))
                .andExpect(jsonPath("$.submissionId").value("flow-sub-1"))
                .andExpect(jsonPath("$.recordedAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        long id = com.jayway.jsonpath.JsonPath
                .parse(res.getResponse().getContentAsString()).read("$.id", Long.class);

        mockMvc.perform(get("/api/scores/me")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(200L, "alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));

        mockMvc.perform(get("/api/scores/" + id)
                        .header("Authorization", BEARER + TestJwtFactory.userToken(200L, "alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("EPL-001"));
    }

    @Test
    void submitAsAdminAlsoWorks() throws Exception {
        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(footballBody(null, "admin-sub-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(9));
    }

    @Test
    void submitWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/api/scores").contentType(MediaType.APPLICATION_JSON)
                        .content(footballBody(null, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateSubmissionIdReturns409() throws Exception {
        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(301L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(footballBody(null, "dup-sub-1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(301L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(footballBody(null, "dup-sub-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void missingSportReturns404() throws Exception {
        when(sportClient.fetchSport(999L)).thenThrow(new ResourceNotFoundException("Sport not found: id 999"));
        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(302L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":999,\"value\":10,\"scoreType\":\"POINTS\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void inactiveSportReturns409() throws Exception {
        when(sportClient.fetchSport(2L)).thenReturn(new SportSnapshot(2L, "CRICKET", "Cricket", false));
        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(303L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cricketBody("inactive-sub-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Sport 'CRICKET' is not active"));
    }

    @Test
    void sportServiceUnavailableReturns503() throws Exception {
        when(sportClient.fetchSport(1L)).thenThrow(new ServiceUnavailableException("Sport Service is unavailable"));
        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(304L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(footballBody(null, null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void userOwnershipEnforcedOnGetById() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(305L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(footballBody("OWNERSHIP-TEST", "own-sub-1")))
                .andExpect(status().isCreated())
                .andReturn();
        long id = com.jayway.jsonpath.JsonPath.parse(res.getResponse().getContentAsString()).read("$.id", Long.class);

        mockMvc.perform(get("/api/scores/" + id)
                        .header("Authorization", BEARER + TestJwtFactory.userToken(306L, "bob")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/scores/" + id)
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void adminSearchWithFilters() throws Exception {
        long searchUser = 307L;
        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(searchUser, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cricketBody("search-crk-1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(searchUser, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(f1Body("search-f1-1")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/scores?userId=" + searchUser)
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/scores?userId=" + searchUser + "&sportId=2")
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sportId").value(2));

        mockMvc.perform(get("/api/scores?userId=" + searchUser + "&scoreType=LAP_TIME")
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].scoreType").value("LAP_TIME"));

        mockMvc.perform(get("/api/scores?userId=99999")
                        .header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void adminDeleteThen404() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(308L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":1,\"value\":99,\"scoreType\":\"POSITION\",\"submissionId\":\"del-sub-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = com.jayway.jsonpath.JsonPath.parse(res.getResponse().getContentAsString()).read("$.id", Long.class);

        mockMvc.perform(delete("/api/scores/" + id).header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/scores/" + id).header("Authorization", BEARER + TestJwtFactory.adminToken(9L, "admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotDeleteEvenOwnScore() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(309L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":1,\"value\":50,\"scoreType\":\"POINTS\",\"submissionId\":\"nodel-sub-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = com.jayway.jsonpath.JsonPath.parse(res.getResponse().getContentAsString()).read("$.id", Long.class);

        mockMvc.perform(delete("/api/scores/" + id).header("Authorization", BEARER + TestJwtFactory.userToken(309L, "alice")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidScoreValueOrTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(310L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":1,\"value\":-5,\"scoreType\":\"POINTS\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/scores")
                        .header("Authorization", BEARER + TestJwtFactory.userToken(310L, "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportId\":1,\"value\":10,\"scoreType\":\"INVALID_TYPE\"}"))
                .andExpect(status().isBadRequest());
    }
}
