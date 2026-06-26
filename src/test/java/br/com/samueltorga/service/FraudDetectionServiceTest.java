package br.com.samueltorga.service;

import br.com.samueltorga.dataset.NormalizationConstants;
import br.com.samueltorga.dto.TransactionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class FraudDetectionServiceTest {

    private static final float TOLERANCE = 1e-4f;

    private static final NormalizationConstants NC = new NormalizationConstants(
            10_000f, 12f, 10f, 1_440f, 1_000f, 20f, 10_000f);

    private static final Map<String, Float> MCC_RISK = Map.of(
            "5411", 0.15f, "5812", 0.30f, "5912", 0.20f,
            "7801", 0.80f, "7802", 0.75f, "7995", 0.85f);

    // ── Vectorização ─────────────────────────────────────────────────────────

    @Test
    void vectorizesLegitTransactionFromSpec() {
        // Exemplo legítimo de DETECTION_RULES.md
        // Vetor esperado: [0.0041, 0.1667, 0.05, 0.7826, 0.3333, -1, -1, 0.0292, 0.15, 0, 1, 0, 0.15, 0.006]
        TransactionRequest tx = new TransactionRequest(
                "tx-1329056812",
                new TransactionRequest.Transaction(41.12, 2, "2026-03-11T18:45:53Z"),
                new TransactionRequest.Customer(82.24, 3, List.of("MERC-003", "MERC-016")),
                new TransactionRequest.Merchant("MERC-016", "5411", 60.25),
                new TransactionRequest.Terminal(false, true, 29.23),
                null);

        float[] v = FraudDetectionService.vectorize(tx, NC, MCC_RISK);

        assertThat(v).hasSize(14);
        assertThat(v[0]).as("amount").isCloseTo(0.0041f, offset(TOLERANCE));
        assertThat(v[1]).as("installments").isCloseTo(0.1667f, offset(TOLERANCE));
        assertThat(v[2]).as("amount_vs_avg").isCloseTo(0.05f, offset(TOLERANCE));
        assertThat(v[3]).as("hour_of_day").isCloseTo(0.7826f, offset(TOLERANCE));
        assertThat(v[4]).as("day_of_week").isCloseTo(0.3333f, offset(TOLERANCE));
        assertThat(v[5]).as("minutes_since_last_tx (null → sentinel)").isEqualTo(-1f);
        assertThat(v[6]).as("km_from_last_tx (null → sentinel)").isEqualTo(-1f);
        assertThat(v[7]).as("km_from_home").isCloseTo(0.0292f, offset(TOLERANCE));
        assertThat(v[8]).as("tx_count_24h").isCloseTo(0.15f, offset(TOLERANCE));
        assertThat(v[9]).as("is_online").isEqualTo(0f);
        assertThat(v[10]).as("card_present").isEqualTo(1f);
        assertThat(v[11]).as("unknown_merchant (merchant known → 0)").isEqualTo(0f);
        assertThat(v[12]).as("mcc_risk").isEqualTo(0.15f);
        assertThat(v[13]).as("merchant_avg_amount").isCloseTo(0.006f, offset(TOLERANCE));
    }

    @Test
    void vectorizesFraudTransactionFromSpec() {
        // Exemplo fraude de DETECTION_RULES.md
        // Vetor esperado: [0.9506, 0.8333, 1.0, 0.2174, 0.8333, -1, -1, 0.9523, 1.0, 0, 1, 1, 0.75, 0.0055]
        TransactionRequest tx = new TransactionRequest(
                "tx-3330991687",
                new TransactionRequest.Transaction(9505.97, 10, "2026-03-14T05:15:12Z"),
                new TransactionRequest.Customer(81.28, 20, List.of("MERC-008", "MERC-007", "MERC-005")),
                new TransactionRequest.Merchant("MERC-068", "7802", 54.86),
                new TransactionRequest.Terminal(false, true, 952.27),
                null);

        float[] v = FraudDetectionService.vectorize(tx, NC, MCC_RISK);

        assertThat(v[0]).as("amount").isCloseTo(0.9506f, offset(TOLERANCE));
        assertThat(v[1]).as("installments").isCloseTo(0.8333f, offset(TOLERANCE));
        assertThat(v[2]).as("amount_vs_avg (overflow → clamped)").isEqualTo(1.0f);
        assertThat(v[3]).as("hour_of_day").isCloseTo(0.2174f, offset(TOLERANCE));
        assertThat(v[4]).as("day_of_week").isCloseTo(0.8333f, offset(TOLERANCE));
        assertThat(v[5]).as("minutes_since_last_tx (null → sentinel)").isEqualTo(-1f);
        assertThat(v[6]).as("km_from_last_tx (null → sentinel)").isEqualTo(-1f);
        assertThat(v[7]).as("km_from_home").isCloseTo(0.9523f, offset(TOLERANCE));
        assertThat(v[8]).as("tx_count_24h (at max → 1)").isEqualTo(1.0f);
        assertThat(v[9]).as("is_online").isEqualTo(0f);
        assertThat(v[10]).as("card_present").isEqualTo(1f);
        assertThat(v[11]).as("unknown_merchant (merchant unknown → 1)").isEqualTo(1f);
        assertThat(v[12]).as("mcc_risk").isEqualTo(0.75f);
        assertThat(v[13]).as("merchant_avg_amount").isCloseTo(0.0055f, offset(TOLERANCE));
    }

    @Test
    void computesMinutesSinceLastTransactionWhenPresent() {
        // last: 14:58:35 → current: 20:23:35 = 325 minutes → 325/1440 = 0.2257
        // km_from_current = 18.86 → 18.86/1000 = 0.01886
        TransactionRequest tx = new TransactionRequest(
                "tx-x",
                new TransactionRequest.Transaction(100, 1, "2026-03-11T20:23:35Z"),
                new TransactionRequest.Customer(100, 1, List.of()),
                new TransactionRequest.Merchant("M-1", "5411", 100),
                new TransactionRequest.Terminal(false, true, 0),
                new TransactionRequest.LastTransaction("2026-03-11T14:58:35Z", 18.86));

        float[] v = FraudDetectionService.vectorize(tx, NC, MCC_RISK);

        assertThat(v[5]).as("minutes_since_last_tx").isCloseTo(0.2257f, offset(TOLERANCE));
        assertThat(v[6]).as("km_from_last_tx").isCloseTo(0.01886f, offset(TOLERANCE));
    }

    @Test
    void usesDefaultMccRiskForUnknownMcc() {
        TransactionRequest tx = new TransactionRequest(
                "tx-x",
                new TransactionRequest.Transaction(100, 1, "2026-03-11T12:00:00Z"),
                new TransactionRequest.Customer(100, 1, List.of()),
                new TransactionRequest.Merchant("M-1", "9999", 100),
                new TransactionRequest.Terminal(false, true, 0),
                null);

        float[] v = FraudDetectionService.vectorize(tx, NC, MCC_RISK);

        assertThat(v[12]).as("mcc_risk default for unknown MCC").isEqualTo(0.5f);
    }

    @Test
    void clampPreventsDimsExceedingOne() {
        TransactionRequest tx = new TransactionRequest(
                "tx-x",
                new TransactionRequest.Transaction(99_999, 99, "2026-03-11T12:00:00Z"),
                new TransactionRequest.Customer(1, 999, List.of()),
                new TransactionRequest.Merchant("M-1", "5411", 99_999),
                new TransactionRequest.Terminal(false, false, 99_999),
                null);

        float[] v = FraudDetectionService.vectorize(tx, NC, MCC_RISK);

        assertThat(v[0]).as("amount clamped").isEqualTo(1.0f);
        assertThat(v[1]).as("installments clamped").isEqualTo(1.0f);
        assertThat(v[2]).as("amount_vs_avg clamped").isEqualTo(1.0f);
        assertThat(v[7]).as("km_from_home clamped").isEqualTo(1.0f);
        assertThat(v[8]).as("tx_count_24h clamped").isEqualTo(1.0f);
        assertThat(v[13]).as("merchant_avg_amount clamped").isEqualTo(1.0f);
    }

    // ── KNN ──────────────────────────────────────────────────────────────────

    @Test
    void knnWithAllFraudNeighborsReturns5() {
        float[] vectors = new float[5 * 14]; // todos zeros, idênticos à query
        boolean[] isfraud = {true, true, true, true, true};

        int count = FraudDetectionService.knn(new float[14], vectors, isfraud, 5);

        assertThat(count).isEqualTo(5);
    }

    @Test
    void knnWithAllLegitNeighborsReturns0() {
        float[] vectors = new float[5 * 14];
        boolean[] isfraud = {false, false, false, false, false};

        int count = FraudDetectionService.knn(new float[14], vectors, isfraud, 5);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void knnPicksClosestNotAll() {
        // 5 fraudes próximas (0.01) + 5 legítimas distantes (0.9)
        // Os 5 mais próximos são todos fraude → fraudCount = 5
        float[] vectors = new float[10 * 14];
        boolean[] isfraud = new boolean[10];
        for (int i = 0; i < 5; i++)  { fill(vectors, i, 0.01f); isfraud[i] = true; }
        for (int i = 5; i < 10; i++) { fill(vectors, i, 0.90f); isfraud[i] = false; }

        int count = FraudDetectionService.knn(new float[14], vectors, isfraud, 10);

        assertThat(count).isEqualTo(5);
    }

    @Test
    void knnCountsMixedLabelsAmong5Nearest() {
        // 2 fraudes próximas + 3 legítimas próximas + 5 fraudes distantes
        // 5 mais próximos: 2 fraud + 3 legit → fraudCount = 2
        float[] vectors = new float[10 * 14];
        boolean[] isfraud = new boolean[10];
        for (int i = 0; i < 2; i++)  { fill(vectors, i, 0.01f); isfraud[i] = true; }
        for (int i = 2; i < 5; i++)  { fill(vectors, i, 0.10f); isfraud[i] = false; }
        for (int i = 5; i < 10; i++) { fill(vectors, i, 0.90f); isfraud[i] = true; }

        int count = FraudDetectionService.knn(new float[14], vectors, isfraud, 10);

        assertThat(count).isEqualTo(2);
    }

    private static void fill(float[] vectors, int index, float value) {
        int base = index * 14;
        for (int d = 0; d < 14; d++) vectors[base + d] = value;
    }
}
