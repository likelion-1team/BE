package com.babylion.team1.domain.upload.service;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties("app.analysis")
class AnalysisPricing {

    private BigDecimal usdKrwRate = BigDecimal.ZERO;
    private String lowTierModel;
    private Map<String, Rate> pricing = Map.of();

    Rate rate(String model) {
        return model == null ? null : pricing.get(model);
    }

    BigDecimal cost(Rate rate, long input, long output) {
        if (rate == null || usdKrwRate.signum() <= 0) {
            return null;
        }
        return rate.input().multiply(BigDecimal.valueOf(input))
                .add(rate.output().multiply(BigDecimal.valueOf(output)))
                .movePointLeft(6).multiply(usdKrwRate);
    }

    record Rate(BigDecimal input, BigDecimal output, String tier) {
    }
}
