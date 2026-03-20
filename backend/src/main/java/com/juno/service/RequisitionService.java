package com.juno.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

/**
 * Loads job requisitions for the JD Generator agent.
 * Ported from Juno agents/jd_generator/tools/.
 */
@Service
public class RequisitionService {

    private static final Logger log = LoggerFactory.getLogger(RequisitionService.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private List<Map<String, Object>> requisitions = List.of();

    public RequisitionService(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void loadRequisitions() {
        try {
            var resource = resourceLoader.getResource("classpath:data/job_requisitions.json");
            requisitions = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            log.info("Loaded {} requisitions", requisitions.size());
        } catch (IOException e) {
            log.error("Failed to load job_requisitions.json: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getAllRequisitions() {
        return requisitions;
    }

    public Map<String, Object> getById(String requisitionId) {
        return requisitions.stream()
                .filter(r -> requisitionId.equals(String.valueOf(r.get("id")))
                        || requisitionId.equals(String.valueOf(r.get("requisitionId"))))
                .findFirst()
                .orElse(null);
    }
}
