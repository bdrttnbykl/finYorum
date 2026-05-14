package com.finyorum.controller;

import com.finyorum.domain.PortfolioAsset;
import com.finyorum.dto.PortfolioAssetRequest;
import com.finyorum.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping("/assets")
    PortfolioAsset addAsset(@Valid @RequestBody PortfolioAssetRequest request) {
        return portfolioService.addAsset(request);
    }

    @GetMapping("/{userId}/assets")
    List<PortfolioAsset> listAssets(@PathVariable Long userId) {
        return portfolioService.listAssets(userId);
    }
}
