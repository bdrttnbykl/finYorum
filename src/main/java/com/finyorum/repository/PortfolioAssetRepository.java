package com.finyorum.repository;

import com.finyorum.domain.PortfolioAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioAssetRepository extends JpaRepository<PortfolioAsset, Long> {
    List<PortfolioAsset> findByUserId(Long userId);
}
