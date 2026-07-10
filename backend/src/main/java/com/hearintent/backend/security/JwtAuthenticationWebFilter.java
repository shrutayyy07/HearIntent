package com.hearintent.backend.security;

import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.UUID;

@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationWebFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            if (!jwtTokenProvider.isValid(token)) {
                return chain.filter(exchange);
            }
            String type = jwtTokenProvider.extractTokenType(token);
            if (!"access".equals(type)) {
                return chain.filter(exchange);
            }

            UUID userId = jwtTokenProvider.extractUserId(token);
            String email = jwtTokenProvider.extractEmail(token);
            AuthenticatedUser principal = new AuthenticatedUser(userId, email);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, Collections.emptyList()
            );

            return chain.filter(exchange)
                    .contextWrite(org.springframework.security.core.context.ReactiveSecurityContextHolder
                            .withSecurityContext(Mono.just(new SecurityContextImpl(authentication))));
        } catch (JwtException | IllegalArgumentException e) {
            return chain.filter(exchange);
        }
    }
}
