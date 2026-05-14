package com.finyorum.controller;

import com.finyorum.dto.QuoteResponse;
import com.finyorum.dto.RiskResponse;
import com.finyorum.service.FinanceService;
import com.finyorum.service.RiskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final FinanceService financeService;
    private final RiskService riskService;

    public StockController(FinanceService financeService, RiskService riskService) {
        this.financeService = financeService;
        this.riskService = riskService;
    }

    @GetMapping("/{symbol}/quote")
    QuoteResponse quote(@PathVariable String symbol) {
        return financeService.quote(symbol);
    }

    @GetMapping("/{symbol}/risk")
    RiskResponse risk(@PathVariable String symbol) {
        return riskService.calculate(symbol);
    }
}
