package com.leasingdocument.backend.service;

import com.leasingdocument.backend.dto.AddAgentRequest;
import com.leasingdocument.backend.entity.Agent;
import com.leasingdocument.backend.repository.AgentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;


    public AgentService(
            AgentRepository agentRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.agentRepository = agentRepository;
        this.passwordEncoder = passwordEncoder;
    }


    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }


    public Agent getAgentById(Long id) {

        return agentRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Agent not found"
                        )
                );
    }


    // =========================================================
    // ADMIN - CREATE NEW AGENT
    // Password is received only for account creation and is
    // encoded before anything is persisted.
    // =========================================================

    public Agent createAgent(
            AddAgentRequest request
    ) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent details are required"
            );
        }


        String fullName =
                cleanRequired(
                        request.fullName(),
                        "Full name",
                        2,
                        120
                );


        String email =
                cleanRequired(
                        request.email(),
                        "Email",
                        5,
                        190
                )
                        .toLowerCase(Locale.ROOT);


        if (
                !email.matches(
                        "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
                )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a valid email address"
            );
        }


        String password =
                request.password();


        if (
                password == null ||
                        password.length() < 8 ||
                        password.length() > 128
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain 8 to 128 characters"
            );
        }


        String nic =
                cleanRequired(
                        request.nic(),
                        "NIC",
                        5,
                        30
                )
                        .toUpperCase(Locale.ROOT);


        String phone =
                cleanRequired(
                        request.phone(),
                        "Phone",
                        7,
                        30
                );


        if (
                agentRepository
                        .findByEmail(email)
                        .isPresent()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An agent with this email already exists"
            );
        }


        Agent agent =
                new Agent();


        agent.setFullName(fullName);
        agent.setEmail(email);

        agent.setPasswordHash(
                passwordEncoder.encode(password)
        );

        agent.setNic(nic);
        agent.setPhone(phone);

        agent.setStatus("ACTIVE");

        agent.setCreatedAt(
                LocalDateTime.now()
        );


        return agentRepository.save(agent);
    }


    // =========================================================
    // EXISTING SAVE METHOD
    // Retained for internal/backend compatibility.
    // Public account creation uses createAgent().
    // =========================================================

    public Agent saveAgent(
            Agent agent
    ) {

        if (
                agent.getStatus() == null ||
                        agent.getStatus().isBlank()
        ) {
            agent.setStatus("ACTIVE");
        }


        if (agent.getCreatedAt() == null) {
            agent.setCreatedAt(
                    LocalDateTime.now()
            );
        }


        return agentRepository.save(agent);
    }


    // =========================================================
    // ADMIN - Disable agent without deleting historical records
    // =========================================================

    public Agent disableAgent(
            Long id
    ) {

        Agent agent =
                getAgentById(id);


        agent.setStatus(
                "DISABLED"
        );


        return agentRepository.save(agent);
    }


    // =========================================================
    // ADMIN - Reactivate previously disabled agent
    // =========================================================

    public Agent reactivateAgent(
            Long id
    ) {

        Agent agent =
                getAgentById(id);


        agent.setStatus(
                "ACTIVE"
        );


        return agentRepository.save(agent);
    }


    // =========================================================
    // LEGACY HARD DELETE
    // =========================================================

    public void deleteAgent(
            Long id
    ) {

        if (
                !agentRepository.existsById(id)
        ) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Agent not found"
            );
        }


        agentRepository.deleteById(id);
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    private String cleanRequired(
            String value,
            String label,
            int minimumLength,
            int maximumLength
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    label + " is required"
            );
        }


        String cleaned =
                value.trim();


        if (
                cleaned.length() < minimumLength ||
                        cleaned.length() > maximumLength
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    label +
                            " must contain " +
                            minimumLength +
                            " to " +
                            maximumLength +
                            " characters"
            );
        }


        return cleaned;
    }
}