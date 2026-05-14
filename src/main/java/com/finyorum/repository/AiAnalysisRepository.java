package com.finyorum.repository;

import com.finyorum.domain.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {
    List<AiAnalysis> findTop10BySymbolOrderByCreatedAtDesc(String symbol);

    Optional<AiAnalysis> findTopBySnapshotIdOrderByCreatedAtDesc(Long snapshotId);
}
