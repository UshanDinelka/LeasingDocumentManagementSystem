package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Agent;
import com.leasingdocument.backend.repository.AgentRepository;
import org.springframework.stereotype.Service;

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
        return agentRepository.findById(id).orElse(null);
    }


    public Agent saveAgent(Agent agent) {
        return agentRepository.save(agent);
    }


    public void deleteAgent(Long id) {
        agentRepository.deleteById(id);
    }
}