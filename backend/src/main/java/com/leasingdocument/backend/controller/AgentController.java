package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.dto.AddAgentRequest;
import com.leasingdocument.backend.entity.Agent;
import com.leasingdocument.backend.service.AgentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;


    public AgentController(
            AgentService agentService
    ) {
        this.agentService = agentService;
    }


    // =========================================================
    // ADMIN ONLY - View all agents
    // =========================================================

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Agent> getAllAgents() {

        return agentService.getAllAgents();
    }


    // =========================================================
    // ADMIN can view any agent
    // AGENT can view only their own profile
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public Agent getAgentById(
            @PathVariable Long id,
            Authentication authentication
    ) {

        String role =
                authentication
                        .getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority();


        String loggedInUserId =
                authentication
                        .getPrincipal()
                        .toString();


        if (
                "ROLE_AGENT".equals(role) &&
                        !loggedInUserId.equals(
                                String.valueOf(id)
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Agents can only view their own profile"
            );
        }


        return agentService.getAgentById(id);
    }


    // =========================================================
    // ADMIN ONLY - Create agent
    // =========================================================

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Agent createAgent(
            @RequestBody AddAgentRequest request
    ) {

        return agentService.createAgent(
                request
        );
    }


    // =========================================================
    // ADMIN ONLY - Disable agent
    // =========================================================

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public Agent disableAgent(
            @PathVariable Long id
    ) {

        return agentService.disableAgent(id);
    }


    // =========================================================
    // ADMIN ONLY - Reactivate agent
    // =========================================================

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public Agent reactivateAgent(
            @PathVariable Long id
    ) {

        return agentService.reactivateAgent(id);
    }


    // =========================================================
    // LEGACY HARD DELETE
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteAgent(
            @PathVariable Long id
    ) {

        agentService.deleteAgent(id);

        return "Agent deleted successfully";
    }
}