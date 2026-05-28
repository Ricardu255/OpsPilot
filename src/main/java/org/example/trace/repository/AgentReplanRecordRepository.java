package org.example.trace.repository;

import org.example.trace.model.AgentReplanRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentReplanRecordRepository extends JpaRepository<AgentReplanRecord, Long> {

    List<AgentReplanRecord> findByTraceIdOrderByCreatedAtAsc(String traceId);
}
