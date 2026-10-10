package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.Session;
import com.leasingdocument.backend.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;


    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }


    // ADMIN ONLY - View all sessions
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Session> getAllSessions() {
        return sessionService.getAllSessions();
    }


    // ADMIN - Any session
    // AGENT - Own session only
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public Session getSessionById(
            @PathVariable Long id,
            Authentication authentication) {

        Session session = sessionService.getSessionById(id);

        if (session == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Session not found"
            );
        }


        String role = authentication.getAuthorities()
                .iterator()
                .next()
                .getAuthority();


        // Admin can view any session
        if ("ROLE_ADMIN".equals(role)) {
            return session;
        }


        // Agent can view only own session
        Long agentId = Long.parseLong(
                authentication.getPrincipal().toString()
        );


        if (!agentId.equals(session.getAgentId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Agents can only view their own sessions"
            );
        }

        return session;
    }


    // ADMIN ONLY - Manual session creation
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Session createSession(
            @RequestBody Session session) {

        return sessionService.saveSession(session);
    }


    // ADMIN ONLY - Delete session
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteSession(
            @PathVariable Long id) {

        sessionService.deleteSession(id);

        return "Session deleted successfully";
    }
}