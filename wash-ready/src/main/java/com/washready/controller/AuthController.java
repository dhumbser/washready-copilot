package com.washready.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.washready.model.User;
import com.washready.repository.UserRepository;
import com.washready.security.JwtService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> login(@RequestParam String usuario, @RequestParam String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(usuario, password));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Usuario deshabilitado"));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Credenciales inválidas"));
        }

        User user = userRepository.findByUsuario(usuario).orElseThrow();
        return ResponseEntity.ok(new TokenResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user)));
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> refresh(@RequestParam("refresh_token") String refreshToken) {
        Jwt jwt;
        try {
            jwt = jwtService.decodeRefreshToken(refreshToken);
        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Refresh token inválido"));
        }

        User user = userRepository.findByUsuario(jwt.getSubject()).orElse(null);
        if (user == null || user.isDisabledNow()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Sesión no disponible"));
        }

        return ResponseEntity.ok(new TokenResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user)));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        User user = userRepository.findByUsuario(jwt.getSubject()).orElseThrow();
        return ResponseEntity.ok(new MeResponse(
                user.getUsuario(),
                user.getCentroTrabajo() != null ? user.getCentroTrabajo().getId() : null,
                user.getCentroTrabajo() != null ? user.getCentroTrabajo().getNombre() : null,
                user.getRole()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken) {
    }

    private record MeResponse(String usuario, Long centroId, String centroTrabajo, String role) {
    }

}