package com.realtimeleaderboard.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realtimeleaderboard.auth.entity.Role;
import com.realtimeleaderboard.auth.entity.User;
import com.realtimeleaderboard.auth.repository.UserRepository;
import com.realtimeleaderboard.auth.security.JwtService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    private String registerBody(String username, String email, String password, String role) {
        StringBuilder sb = new StringBuilder("{");
        sb.append(String.format("\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"", username, email, password));
        if (role != null) {
            sb.append(String.format(",\"role\":\"%s\"", role));
        }
        return sb.append("}").toString();
    }

    private String loginBody(String username, String password) {
        return String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
    }

    private String[] registerAndLogin(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, email, password, null)))
                .andExpect(status().isCreated());
        return obtainTokens(username, password);
    }

    /** Returns [accessToken, refreshToken]. */
    private String[] obtainTokens(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper().readValue(response, Map.class);
        return new String[]{(String) map.get("accessToken"), (String) map.get("refreshToken")};
    }

    @Test
    void registrationReturnsSafePayloadWithoutPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("john", "john@example.com", "secret123", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateUsernameConflicts() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("bob", "bob@example.com", "secret123", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("bob", "other@example.com", "secret123", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void duplicateEmailConflicts() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("carol", "carol@example.com", "secret123", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("carol2", "carol@example.com", "secret123", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationPayloadReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    void loginIssuesTokensAndRoleDefaultsToUser() throws Exception {
        registerAndLogin("dave", "dave@example.com", "secret123");
        // covered by obtainTokens; explicit assertions here:
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("dave", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("accessToken").contains("refreshToken");
    }

    @Test
    void invalidLoginIsUnauthorizedWithGenericMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("erin", "erin@example.com", "secret123", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("erin", "wrongpass9")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ghost_user", "whatever1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void inactiveUserCannotAuthenticate() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("frank", "frank@example.com", "secret123", null)))
                .andExpect(status().isCreated());
        User frank = userRepository.findByUsername("frank").orElseThrow();
        frank.setActive(false);
        userRepository.save(frank);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("frank", "secret123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void meAcceptsValidAccessToken() throws Exception {
        String token = registerAndLogin("grace", "grace@example.com", "secret123")[0];
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("grace"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.email").value("grace@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void meRejectsGarbageAndExpiredTokens() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer garbage.token.here"))
                .andExpect(status().isUnauthorized());

        JwtService expiredSigner = new JwtService(
                "unit-test-only-secret-key-0123456789abcdef0123456789abcdef", -1000);
        String expired = expiredSigner.generateAccessToken(999L, "expiredUser", "USER");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessAdminEndpointButAdminCan() throws Exception {
        String userToken = registerAndLogin("hana", "hana@example.com", "secret123")[0];

        mockMvc.perform(get("/api/auth/admin/check").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        User admin = new User();
        admin.setUsername("rootadmin");
        admin.setEmail("admin@example.com");
        admin.setPassword(passwordEncoder.encode("adminpass1"));
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
        String adminToken = obtainTokens("rootadmin", "adminpass1")[0];

        mockMvc.perform(get("/api/auth/admin/check").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ADMIN access confirmed"));
    }

    @Test
    void refreshFlowWorksThenLogoutRevokesTheRefreshToken() throws Exception {
        String[] tokens = registerAndLogin("ivan", "ivan@example.com", "secret123");

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", tokens[1])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + tokens[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", tokens[1])))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", tokens[1])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRejectsUnknownToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"totally-unknown-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storedPasswordIsBcryptNeverPlaintext() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("kate", "kate@example.com", "secret123", null)))
                .andExpect(status().isCreated());
        User kate = userRepository.findByUsername("kate").orElseThrow();
        assertThat(kate.getPassword()).startsWith("$2a$").doesNotContain("secret123");
        assertThat(passwordEncoder.matches("secret123", kate.getPassword())).isTrue();
    }
}
