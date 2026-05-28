package org.example.trace.repository;

import org.example.trace.model.AgentTraceStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentTraceStepRepository extends JpaRepository<AgentTraceStep, Long> {

    List<AgentTraceStep> findByTraceIdOrderByStartTimeAsc(String traceId);
}
