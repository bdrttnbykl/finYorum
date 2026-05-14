package com.finyorum.repository;

import com.finyorum.domain.CryptoMarketSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CryptoMarketSnapshotRepository extends JpaRepository<CryptoMarketSnapshot, Long> {
    Optional<CryptoMarketSnapshot> findTopBySymbolOrderByFetchedAtDesc(String symbol);
}
