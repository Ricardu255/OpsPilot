package org.example.trace.repository;

import org.example.trace.model.AgentToolCallTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentToolCallTraceRepository extends JpaRepository<AgentToolCallTrace, Long> {

    List<AgentToolCallTrace> findByTraceIdOrderByStartedAtAsc(String traceId);

    long countByStatus(String status);
}
