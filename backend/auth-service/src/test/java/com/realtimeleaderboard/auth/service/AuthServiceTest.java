package com.realtimeleaderboard.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.realtimeleaderboard.auth.dto.request.LoginRequest;
import com.realtimeleaderboard.auth.dto.request.RegisterRequest;
import com.realtimeleaderboard.auth.dto.response.AuthResponse;
import com.realtimeleaderboard.auth.dto.response.RegistrationResponse;
import com.realtimeleaderboard.auth.entity.Role;
import com.realtimeleaderboard.auth.entity.User;
import com.realtimeleaderboard.auth.exception.DuplicateResourceException;
import com.realtimeleaderboard.auth.exception.InvalidCredentialsException;
import com.realtimeleaderboard.auth.repository.UserRepository;
import com.realtimeleaderboard.auth.security.JwtService;
import com.realtimeleaderboard.auth.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenService, passwordEncoder, jwtService,
                authenticationManager);
    }

    private RegisterRequest registerRequest(String role) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john");
        request.setEmail("john@example.com");
        request.setPassword("secret123");
        request.setRole(role);
        return request;
    }

    @Test
    void registerHashesPasswordAndForcesUserRole() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response = authService.register(registerRequest("ADMIN"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(response.getRole()).isEqualTo("USER");
        assertThat(response.getUsername()).isEqualTo("john");
        assertThat(response.getMessage()).isEqualTo("Registration successful");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest(null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Username");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest(null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email");
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokensOnSuccess() {
        LoginRequest request = new LoginRequest();
        request.setUsername("john");
        request.setPassword("secret123");

        Authentication authentication = mock(Authentication.class);
        UserPrincipal principal = new UserPrincipal(7L, "john", "USER", "$2a$10$hashed", true);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(jwtService.generateAccessToken(7L, "john", "USER")).thenReturn("access.jwt.token");
        when(refreshTokenService.create(7L)).thenReturn("raw-refresh-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access.jwt.token");
        assertThat(response.getRefreshToken()).isEqualTo("raw-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    void loginThrowsGenericInvalidCredentialsOnBadPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("john");
        request.setPassword("wrongpass1");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).create(any());
    }

    @Test
    void loginThrowsGenericInvalidCredentialsForInactiveUser() {
        LoginRequest request = new LoginRequest();
        request.setUsername("john");
        request.setPassword("secret123");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshIssuesAccessTokenFromStoredRefreshToken() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setUsername("john");
        user.setRole(Role.USER);
        user.setActive(true);
        when(refreshTokenService.validateAndGetUser("raw-refresh-token")).thenReturn(user);
        when(jwtService.generateAccessToken(7L, "john", "USER")).thenReturn("new.access.jwt");

        AuthResponse response = authService.refresh("raw-refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("new.access.jwt");
        assertThat(response.getRefreshToken()).isEqualTo("raw-refresh-token");
    }

    @Test
    void logoutRevokesRefreshToken() {
        authService.logout("raw-refresh-token");
        verify(refreshTokenService).revoke("raw-refresh-token");
    }
}
