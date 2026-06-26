package br.com.samueltorga.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NormalizationConstantsTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Test
    void matchesActualNormalizationJson() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/data/normalization.json")) {
            assertNotNull(is, "normalization.json not found on classpath");
            NormalizationConstants nc = mapper.readValue(is, NormalizationConstants.class);

            assertEquals(10_000f, nc.maxAmount());
            assertEquals(12f, nc.maxInstallments());
            assertEquals(10f, nc.amountVsAvgRatio());
            assertEquals(1_440f, nc.maxMinutes());
            assertEquals(1_000f, nc.maxKm());
            assertEquals(20f, nc.maxTxCount24h());
            assertEquals(10_000f, nc.maxMerchantAvgAmount());
        }
    }
}
