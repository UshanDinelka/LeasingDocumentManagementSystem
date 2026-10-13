package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Agent;
import com.leasingdocument.backend.repository.AgentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AgentService {

    private final AgentRepository agentRepository;


    public AgentService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
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


    public Agent saveAgent(Agent agent) {

        if (agent.getStatus() == null || agent.getStatus().isBlank()) {
            agent.setStatus("ACTIVE");
        }

        return agentRepository.save(agent);
    }


    // =========================================================
    // ADMIN - Disable agent without deleting historical records
    // =========================================================

    public Agent disableAgent(Long id) {

        Agent agent = getAgentById(id);

        agent.setStatus("DISABLED");

        return agentRepository.save(agent);
    }


    // =========================================================
    // ADMIN - Reactivate previously disabled agent
    // =========================================================

    public Agent reactivateAgent(Long id) {

        Agent agent = getAgentById(id);

        agent.setStatus("ACTIVE");

        return agentRepository.save(agent);
    }


    // =========================================================
    // LEGACY HARD DELETE
    // Kept only for backend compatibility.
    // Do not use this from the Manage Agents UI.
    // =========================================================

    public void deleteAgent(Long id) {

        if (!agentRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Agent not found"
            );
        }

        agentRepository.deleteById(id);
    }
}
