package org.example.trace.repository;

import org.example.trace.model.AgentSessionTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentSessionTraceRepository extends JpaRepository<AgentSessionTrace, Long> {

    Optional<AgentSessionTrace> findTopByTraceIdOrderByIdDesc(String traceId);

    List<AgentSessionTrace> findByTraceId(String traceId);

    List<AgentSessionTrace> findByParentTraceIdOrderByStartTimeAsc(String parentTraceId);

    Optional<AgentSessionTrace> findBySessionId(String sessionId);

    Page<AgentSessionTrace> findAllByOrderByStartTimeDesc(Pageable pageable);

    @Query("SELECT s FROM AgentSessionTrace s WHERE " +
            "(:agentType IS NULL OR s.agentType = :agentType) AND " +
            "(:status IS NULL OR s.status = :status) AND " +
            "(:startFrom IS NULL OR s.startTime >= :startFrom) AND " +
            "(:startTo IS NULL OR s.startTime <= :startTo) " +
            "ORDER BY s.startTime DESC")
    Page<AgentSessionTrace> findByFilters(
            @Param("agentType") String agentType,
            @Param("status") String status,
            @Param("startFrom") LocalDateTime startFrom,
            @Param("startTo") LocalDateTime startTo,
            Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT COALESCE(AVG(s.costMs), 0) FROM AgentSessionTrace s WHERE s.costMs IS NOT NULL")
    double averageCostMs();

    List<AgentSessionTrace> findTop10ByStatusOrderByStartTimeDesc(String status);

    List<AgentSessionTrace> findTop10ByOrderByCostMsDesc();
}
