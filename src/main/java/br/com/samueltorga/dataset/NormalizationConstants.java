package br.com.samueltorga.dataset;

public record NormalizationConstants(
        float maxAmount,
        float maxInstallments,
        float amountVsAvgRatio,
        float maxMinutes,
        float maxKm,
        float maxTxCount24h,
        float maxMerchantAvgAmount
) {}
