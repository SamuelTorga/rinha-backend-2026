package br.com.samueltorga.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import br.com.samueltorga.dataset.DatasetLoader;
import br.com.samueltorga.dataset.NormalizationConstants;
import br.com.samueltorga.dto.FraudScoreResponse;
import br.com.samueltorga.dto.TransactionRequest;

/**
 * Fraud detection pipeline: vectorize → KNN → score.
 *
 * <p>{@link #vectorize} and {@link #knn} are package-private statics so they can be
 * unit-tested without CDI. {@link #evaluate} is the only entry point for production code.
 */
@ApplicationScoped
public class FraudDetectionService {

    @Inject
    DatasetLoader dataset;

    /**
     * Evaluates whether a transaction is fraudulent.
     *
     * <p>Threshold: {@code fraud_score >= 0.6} → rejected. Fixed by the competition spec.
     */
    public FraudScoreResponse evaluate(TransactionRequest tx) {
        float[] vector = vectorize(tx, dataset.normalization(), dataset.mccRisk());
        int fraudCount = knn(vector, dataset.vectors(), dataset.isfraud(), dataset.vectorCount());
        float score = fraudCount / 5.0f;
        return new FraudScoreResponse(score < 0.6f, score);
    }

    /**
     * Transforms a transaction payload into a 14-dimensional float vector.
     *
     * <p>All dimensions are clamped to {@code [0.0, 1.0]} except indices 5 and 6, which carry
     * {@code -1f} when {@code last_transaction} is {@code null}. That sentinel is the only
     * value outside {@code [0, 1]} and must not be replaced or filtered — the reference
     * dataset uses the same convention, so KNN naturally groups "no-history" transactions.
     *
     * <p>Full spec: <a href="https://github.com/zanfranceschi/rinha-de-backend-2026/blob/main/docs/en/DETECTION_RULES.md">DETECTION_RULES.md</a>
     *
     * @param tx      incoming transaction payload
     * @param nc      normalization constants loaded from {@code normalization.json}
     * @param mccRisk MCC → risk score map; unknown MCCs default to {@code 0.5f}
     * @return 14-element vector ready for KNN search
     */
    static float[] vectorize(TransactionRequest tx, NormalizationConstants nc, Map<String, Float> mccRisk) {
        TransactionRequest.Transaction trx = tx.transaction();
        TransactionRequest.Customer cust = tx.customer();
        TransactionRequest.Merchant merch = tx.merchant();
        TransactionRequest.Terminal term = tx.terminal();

        float[] v = new float[14];

        v[0] = clamp((float) trx.amount() / nc.maxAmount());
        v[1] = clamp(trx.installments() / nc.maxInstallments());
        v[2] = clamp((float) (trx.amount() / cust.avgAmount()) / nc.amountVsAvgRatio());

        OffsetDateTime requestedAt = OffsetDateTime.parse(trx.requestedAt());
        v[3] = requestedAt.getHour() / 23.0f;
        // DayOfWeek.getValue() returns 1 (Mon) – 7 (Sun); spec wants 0 (Mon) – 6 (Sun)
        v[4] = (requestedAt.getDayOfWeek().getValue() - 1) / 6.0f;

        if (tx.lastTransaction() != null) {
            OffsetDateTime lastAt = OffsetDateTime.parse(tx.lastTransaction().timestamp());
            long minutes = Duration.between(lastAt, requestedAt).toMinutes();
            v[5] = clamp(minutes / nc.maxMinutes());
            v[6] = clamp((float) tx.lastTransaction().kmFromCurrent() / nc.maxKm());
        } else {
            v[5] = -1f;
            v[6] = -1f;
        }

        v[7]  = clamp((float) term.kmFromHome() / nc.maxKm());
        v[8]  = clamp(cust.txCount24h() / nc.maxTxCount24h());
        v[9]  = term.isOnline() ? 1f : 0f;
        v[10] = term.cardPresent() ? 1f : 0f;
        // dim[11] is 1 when the merchant is UNKNOWN (inverted — higher = more suspicious)
        v[11] = cust.knownMerchants().contains(merch.id()) ? 0f : 1f;
        v[12] = mccRisk.getOrDefault(merch.mcc(), 0.5f);
        v[13] = clamp((float) merch.avgAmount() / nc.maxMerchantAvgAmount());

        return v;
    }

    /**
     * Brute-force k=5 nearest-neighbour search over the reference dataset.
     *
     * <p>Uses <em>squared</em> Euclidean distance: the square root is never computed because
     * it is a monotone transformation that does not change the neighbour ordering, saving
     * ~1 M {@code Math.sqrt} calls per request.
     *
     * <p>The top-5 slot with the worst (largest) distance is replaced whenever a closer
     * candidate is found. With k=5 fixed, a linear scan of 5 slots per iteration is faster
     * than a heap due to lower constant factors and better cache behaviour.
     *
     * @param query   vectorized transaction (14 floats)
     * @param vectors flat reference array; vector {@code i} starts at {@code i * 14}
     * @param isfraud label array parallel to {@code vectors}
     * @param count   number of valid entries in {@code vectors} / {@code isfraud}
     * @return number of fraud labels among the 5 nearest neighbours (0–5)
     */
    static int knn(float[] query, float[] vectors, boolean[] isfraud, int count) {
        float[] topDist = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        boolean[] topFraud = new boolean[5];

        for (int i = 0; i < count; i++) {
            float dist = squaredDistance(query, vectors, i * 14);
            int worst = worstIndex(topDist);
            if (dist < topDist[worst]) {
                topDist[worst] = dist;
                topFraud[worst] = isfraud[i];
            }
        }

        int fraudCount = 0;
        for (boolean f : topFraud) {
            if (f) {
                fraudCount++;
            }
        }
        return fraudCount;
    }

    private static float squaredDistance(float[] query, float[] vectors, int offset) {
        float sum = 0f;
        for (int i = 0; i < 14; i++) {
            float d = query[i] - vectors[offset + i];
            sum += d * d;
        }
        return sum;
    }

    private static int worstIndex(float[] dist) {
        int idx = 0;
        for (int i = 1; i < 5; i++) {
            if (dist[i] > dist[idx]) {
                idx = i;
            }
        }
        return idx;
    }

    private static float clamp(float x) {
        return Math.clamp(x, 0f, 1f);
    }
}
