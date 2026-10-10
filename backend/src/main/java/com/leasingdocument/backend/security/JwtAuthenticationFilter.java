package com.leasingdocument.backend.security;

import com.leasingdocument.backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtService jwtService;


    public JwtAuthenticationFilter(
            JwtService jwtService
    ) {

        this.jwtService =
                jwtService;
    }


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {


        String authHeader =
                request.getHeader(
                        "Authorization"
                );


        String token = null;


        // =====================================================
        // CHECK BEARER TOKEN
        // =====================================================

        if (
                authHeader != null &&
                        authHeader.startsWith(
                                "Bearer "
                        )
        ) {

            token =
                    authHeader.substring(7);


            // =================================================
            // VALIDATE JWT
            // =================================================

            if (
                    jwtService.isTokenValid(
                            token
                    )
            ) {

                String userId =
                        jwtService
                                .extractUserId(
                                        token
                                );


                String role =
                        jwtService
                                .extractRole(
                                        token
                                );


                // =============================================
                // CREATE AUTHORITY
                // =============================================

                SimpleGrantedAuthority authority =
                        new SimpleGrantedAuthority(
                                "ROLE_" + role
                        );


                // =============================================
                // CREATE AUTHENTICATION
                // =============================================

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(authority)
                        );


                // =============================================
                // SET SECURITY CONTEXT
                // =============================================

                SecurityContext context =
                        SecurityContextHolder
                                .createEmptyContext();


                context.setAuthentication(
                        authentication
                );


                SecurityContextHolder
                        .setContext(
                                context
                        );
            }
        }


        // =====================================================
        // CONTINUE REQUEST
        // =====================================================

        filterChain.doFilter(
                request,
                response
        );


        // =====================================================
        // REVOKE TOKEN AFTER SUCCESSFUL LOGOUT
        // =====================================================

        if (
                token != null &&
                        request.getRequestURI()
                                .equals(
                                        "/api/auth/logout"
                                ) &&
                        request.getMethod()
                                .equalsIgnoreCase(
                                        "POST"
                                ) &&
                        response.getStatus() >= 200 &&
                        response.getStatus() < 300
        ) {

            jwtService.revokeToken(
                    token
            );
        }
    }
}