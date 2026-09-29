package com.example.taskmanagement.config;

import com.example.taskmanagement.security.JsonErrorWriter;
import com.example.taskmanagement.security.JwtAuthenticationFilter;
import com.example.taskmanagement.security.RateLimitFilter;
import com.example.taskmanagement.security.RequestIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.rate-limit.enabled}")
    private boolean rateLimitEnabled;

    @Value("${app.rate-limit.capacity}")
    private int rateLimitCapacity;

    @Value("${app.rate-limit.refill-per-second}")
    private double rateLimitRefillPerSecond;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, AuthenticationProvider authenticationProvider) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(req -> req
                        .requestMatchers("/api/auth/**", "/actuator/health", "/actuator/health/**").permitAll()
                        // API docs and the landing redirect are public, so anyone can explore the API
                        .requestMatchers("/", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Without these, Spring answers a missing/invalid token with 403; the correct status is 401.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                JsonErrorWriter.write(response, 401, "Authentication required: missing, invalid or expired token."))
                        .accessDeniedHandler((request, response, e) ->
                                JsonErrorWriter.write(response, 403, "Access denied: You do not have the required role."))
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // Cheapest checks first: tag the request, then rate limit, then authenticate.
        if (rateLimitEnabled) {
            http.addFilterBefore(new RateLimitFilter(rateLimitCapacity, rateLimitRefillPerSecond), JwtAuthenticationFilter.class);
            http.addFilterBefore(new RequestIdFilter(), RateLimitFilter.class);
        } else {
            http.addFilterBefore(new RequestIdFilter(), JwtAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After", "Location"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
