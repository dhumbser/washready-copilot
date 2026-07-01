package com.washready.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import com.washready.model.User;

@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_CENTRO_ID = "centroId";
    private static final String CLAIM_TOKEN_USE = "token_use";
    private static final String TOKEN_USE_ACCESS = "access";
    private static final String TOKEN_USE_REFRESH = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder,
            @Value("${jwt.access-token-ttl}") String accessTokenTtl,
            @Value("${jwt.refresh-token-ttl}") String refreshTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.accessTokenTtl = Duration.parse(accessTokenTtl);
        this.refreshTokenTtl = Duration.parse(refreshTokenTtl);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("wash-ready")
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(user.getUsuario())
                .claim(CLAIM_TOKEN_USE, TOKEN_USE_ACCESS)
                .claim(CLAIM_ROLE, user.getRole());

        if (user.getCentroTrabajo() != null) {
            claims.claim(CLAIM_CENTRO_ID, user.getCentroTrabajo().getId());
        }

        return encode(claims.build());
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("wash-ready")
                .issuedAt(now)
                .expiresAt(now.plus(refreshTokenTtl))
                .subject(user.getUsuario())
                .claim(CLAIM_TOKEN_USE, TOKEN_USE_REFRESH)
                .build();

        return encode(claims);
    }

    public Jwt decodeRefreshToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        if (!TOKEN_USE_REFRESH.equals(jwt.getClaimAsString(CLAIM_TOKEN_USE))) {
            throw new JwtException("El token proporcionado no es un refresh token");
        }
        return jwt;
    }

    private String encode(JwtClaimsSet claims) {
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

}