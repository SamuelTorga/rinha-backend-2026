package br.com.samueltorga.dataset;

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationConstantsTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Test
    void matchesActualNormalizationJson() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/data/normalization.json")) {
            assertThat(is).as("normalization.json not found on classpath").isNotNull();
            NormalizationConstants nc = mapper.readValue(is, NormalizationConstants.class);

            assertThat(nc.maxAmount()).isEqualTo(10_000f);
            assertThat(nc.maxInstallments()).isEqualTo(12f);
            assertThat(nc.amountVsAvgRatio()).isEqualTo(10f);
            assertThat(nc.maxMinutes()).isEqualTo(1_440f);
            assertThat(nc.maxKm()).isEqualTo(1_000f);
            assertThat(nc.maxTxCount24h()).isEqualTo(20f);
            assertThat(nc.maxMerchantAvgAmount()).isEqualTo(10_000f);
        }
    }
}
