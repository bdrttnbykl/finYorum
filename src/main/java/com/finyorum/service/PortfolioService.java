package com.finyorum.service;

import com.finyorum.domain.PortfolioAsset;
import com.finyorum.dto.PortfolioAssetRequest;
import com.finyorum.repository.PortfolioAssetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioAssetRepository assets;

    public PortfolioService(PortfolioAssetRepository assets) {
        this.assets = assets;
    }

    public PortfolioAsset addAsset(PortfolioAssetRequest request) {
        return assets.save(new PortfolioAsset(
                request.userId(),
                request.symbol(),
                request.quantity(),
                request.averagePrice()
        ));
    }

    public List<PortfolioAsset> listAssets(Long userId) {
        return assets.findByUserId(userId);
    }
}
