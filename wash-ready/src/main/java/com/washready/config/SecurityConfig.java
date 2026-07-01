package com.washready.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final DeviceStatusFilter deviceStatusFilter;

    public SecurityConfig(DeviceStatusFilter deviceStatusFilter) {
        this.deviceStatusFilter = deviceStatusFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http, UserDetailsService uds) throws Exception {
        var builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.userDetailsService(uds).passwordEncoder(passwordEncoder());
        return builder.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var role = jwt.getClaimAsString("role"); // "ROLE_ADMIN" o "ROLE_USER"
            return (role != null && !role.isBlank())
                ? List.of(new SimpleGrantedAuthority(role))
                : List.of();
        });
        return converter;
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        return request -> {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
            return null; // no leemos de cookies
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                .requestMatchers(HttpMethod.GET, "/login.html", "/", "/index", "/index.html",
                        "/clientes", "/vehiculos", "/servicios", "/informes", "/adelantos",
                        "/admin/dashboard", "/admin/usuarios", "/admin/centros", "/admin/empresas",
                        "/tickets/buscar", "/tickets/crear", "/pdf_viewer.html").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh").permitAll()
                // Confirmación por enlace de correo (token): se abren sin sesión, directamente desde el email
                .requestMatchers("/api/adelantos/confirm", "/api/tickets/anulacion/confirm", "/api/clientes/no-deseado/confirm").permitAll()
                // /api/users/min es accesible a cualquier usuario autenticado (operarios en tickets)
                .requestMatchers(HttpMethod.GET, "/api/users/min").authenticated()
                // El resto de /api/users solo lo puede usar ROLE_ADMIN
                .requestMatchers("/api/users", "/api/users/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver())
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .addFilterAfter(deviceStatusFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

}