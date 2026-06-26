package br.com.samueltorga.service;

import br.com.samueltorga.dataset.DatasetLoader;
import br.com.samueltorga.dataset.NormalizationConstants;
import br.com.samueltorga.dto.FraudScoreResponse;
import br.com.samueltorga.dto.TransactionRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@ApplicationScoped
public class FraudDetectionService {

    @Inject
    DatasetLoader dataset;

    public FraudScoreResponse evaluate(TransactionRequest tx) {
        float[] vector = vectorize(tx, dataset.normalization(), dataset.mccRisk());
        int fraudCount = knn(vector, dataset.vectors(), dataset.isfraud(), dataset.vectorCount());
        float score = fraudCount / 5.0f;
        return new FraudScoreResponse(score < 0.6f, score);
    }

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
        v[11] = cust.knownMerchants().contains(merch.id()) ? 0f : 1f;
        v[12] = mccRisk.getOrDefault(merch.mcc(), 0.5f);
        v[13] = clamp((float) merch.avgAmount() / nc.maxMerchantAvgAmount());

        return v;
    }

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
        for (boolean f : topFraud) if (f) fraudCount++;
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
            if (dist[i] > dist[idx]) idx = i;
        }
        return idx;
    }

    private static float clamp(float x) {
        return Math.max(0f, Math.min(1f, x));
    }
}
