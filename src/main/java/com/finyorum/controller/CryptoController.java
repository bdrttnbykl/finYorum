package com.finyorum.controller;

import com.finyorum.dto.CryptoSearchResult;
import com.finyorum.dto.AiAnalysisResponse;
import com.finyorum.dto.CryptoDashboardResponse;
import com.finyorum.dto.CryptoMarketStats;
import com.finyorum.dto.MarketChartResponse;
import com.finyorum.dto.QuoteResponse;
import com.finyorum.dto.RiskResponse;
import com.finyorum.service.CryptoDashboardService;
import com.finyorum.service.CryptoService;
import com.finyorum.service.RiskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crypto")
public class CryptoController {

    private final CryptoService cryptoService;
    private final RiskService riskService;
    private final CryptoDashboardService cryptoDashboardService;

    public CryptoController(CryptoService cryptoService,
                            RiskService riskService,
                            CryptoDashboardService cryptoDashboardService) {
        this.cryptoService = cryptoService;
        this.riskService = riskService;
        this.cryptoDashboardService = cryptoDashboardService;
    }

    @GetMapping("/{asset}/quote")
    QuoteResponse quote(@PathVariable String asset) {
        return cryptoService.quote(asset);
    }

    @GetMapping("/{asset}/market")
    CryptoMarketStats market(@PathVariable String asset) {
        return cryptoService.marketStats(asset);
    }

    @GetMapping("/{asset}/dashboard")
    CryptoDashboardResponse dashboard(@PathVariable String asset,
                                      @RequestParam(defaultValue = "30") int days) {
        return cryptoDashboardService.dashboard(asset, days);
    }

    @GetMapping("/{asset}/market-chart")
    MarketChartResponse marketChart(@PathVariable String asset,
                                    @RequestParam(defaultValue = "30") int days) {
        return cryptoService.marketChart(asset, days);
    }

    @GetMapping("/{asset}/risk")
    RiskResponse risk(@PathVariable String asset) {
        MarketChartResponse chart = cryptoService.marketChart(asset, 30);
        return riskService.calculateFromPrices(
                asset,
                chart.prices().stream().map(point -> point.price()).toList()
        );
    }

    @GetMapping("/search")
    List<CryptoSearchResult> search(@RequestParam String query) {
        return cryptoService.search(query);
    }
}
