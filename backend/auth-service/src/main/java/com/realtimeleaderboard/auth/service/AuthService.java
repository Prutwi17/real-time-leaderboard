package com.realtimeleaderboard.auth.service;

import com.realtimeleaderboard.auth.dto.request.LoginRequest;
import com.realtimeleaderboard.auth.dto.request.RegisterRequest;
import com.realtimeleaderboard.auth.dto.response.AuthResponse;
import com.realtimeleaderboard.auth.dto.response.MeResponse;
import com.realtimeleaderboard.auth.dto.response.RegistrationResponse;
import com.realtimeleaderboard.auth.entity.Role;
import com.realtimeleaderboard.auth.entity.User;
import com.realtimeleaderboard.auth.exception.DuplicateResourceException;
import com.realtimeleaderboard.auth.exception.InvalidCredentialsException;
import com.realtimeleaderboard.auth.exception.InvalidTokenException;
import com.realtimeleaderboard.auth.repository.UserRepository;
import com.realtimeleaderboard.auth.security.JwtService;
import com.realtimeleaderboard.auth.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Public registration always creates USER accounts. The role field of the request
     * is deliberately ignored so clients cannot self-elevate to ADMIN.
     */
    public RegistrationResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setActive(true);
        User saved = userRepository.save(user);

        return new RegistrationResponse("Registration successful", saved.getId(), saved.getUsername(),
                saved.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            String accessToken = jwtService.generateAccessToken(principal.userId(), principal.username(),
                    principal.role());
            String refreshToken = refreshTokenService.create(principal.userId());
            return new AuthResponse(accessToken, refreshToken, "Bearer",
                    jwtService.getAccessTokenExpirationSeconds(), principal.userId(), principal.username(),
                    principal.role());
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }
    }

    public AuthResponse refresh(String rawRefreshToken) {
        User user = refreshTokenService.validateAndGetUser(rawRefreshToken);
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        return new AuthResponse(accessToken, rawRefreshToken, "Bearer", jwtService.getAccessTokenExpirationSeconds(),
                user.getId(), user.getUsername(), user.getRole().name());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Unknown user"));
        return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }
}
