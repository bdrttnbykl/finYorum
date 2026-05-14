package com.finyorum.service;

import com.finyorum.dto.RiskResponse;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RiskService {

    public RiskResponse calculate(String symbol) {
        double[] dailyReturns = syntheticReturns(symbol.toUpperCase());
        return calculate(symbol, dailyReturns);
    }

    public RiskResponse calculateFromPrices(String symbol, List<BigDecimal> prices) {
        if (prices.size() < 3) {
            return calculate(symbol);
        }

        double[] returns = new double[prices.size() - 1];
        for (int i = 1; i < prices.size(); i++) {
            double previous = prices.get(i - 1).doubleValue();
            double current = prices.get(i).doubleValue();
            returns[i - 1] = previous == 0 ? 0 : (current - previous) / previous;
        }
        return calculate(symbol, returns);
    }

    private RiskResponse calculate(String symbol, double[] dailyReturns) {
        double volatility = new StandardDeviation().evaluate(dailyReturns) * Math.sqrt(252);
        double averageReturn = average(dailyReturns) * 252;
        double sharpeRatio = volatility == 0 ? 0 : (averageReturn - 0.03) / volatility;
        String level = volatility > 0.45 ? "HIGH" : volatility > 0.25 ? "MEDIUM" : "LOW";

        return new RiskResponse(
                symbol.toUpperCase(),
                BigDecimal.valueOf(volatility).setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(sharpeRatio).setScale(4, RoundingMode.HALF_UP),
                level
        );
    }

    private double[] syntheticReturns(String symbol) {
        double[] returns = new double[30];
        int seed = Math.abs(symbol.hashCode());
        for (int i = 0; i < returns.length; i++) {
            returns[i] = (((seed >> (i % 12)) % 21) - 10) / 1000.0;
        }
        return returns;
    }

    private double average(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
