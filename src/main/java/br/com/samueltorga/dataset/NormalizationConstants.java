package br.com.samueltorga.dataset;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NormalizationConstants(
        float maxAmount,
        float maxInstallments,
        float amountVsAvgRatio,
        float maxMinutes,
        float maxKm,
        @JsonProperty("max_tx_count_24h") float maxTxCount24h,
        float maxMerchantAvgAmount
) {}
