package br.com.samueltorga.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRequestTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Test
    void deserializesFullPayload() throws Exception {
        String json = """
                {
                  "id": "tx-3576980410",
                  "transaction": { "amount": 384.88, "installments": 3, "requested_at": "2026-03-11T20:23:35Z" },
                  "customer": { "avg_amount": 769.76, "tx_count_24h": 3, "known_merchants": ["MERC-009", "MERC-001"] },
                  "merchant": { "id": "MERC-001", "mcc": "5912", "avg_amount": 298.95 },
                  "terminal": { "is_online": false, "card_present": true, "km_from_home": 13.709 },
                  "last_transaction": { "timestamp": "2026-03-11T14:58:35Z", "km_from_current": 18.862 }
                }
                """;

        TransactionRequest req = mapper.readValue(json, TransactionRequest.class);

        assertThat(req.id()).isEqualTo("tx-3576980410");
        assertThat(req.transaction().amount()).isEqualTo(384.88);
        assertThat(req.transaction().installments()).isEqualTo(3);
        assertThat(req.transaction().requestedAt()).isEqualTo("2026-03-11T20:23:35Z");
        assertThat(req.customer().avgAmount()).isEqualTo(769.76);
        assertThat(req.customer().txCount24h()).isEqualTo(3);
        assertThat(req.customer().knownMerchants()).containsExactly("MERC-009", "MERC-001");
        assertThat(req.merchant().id()).isEqualTo("MERC-001");
        assertThat(req.merchant().mcc()).isEqualTo("5912");
        assertThat(req.merchant().avgAmount()).isEqualTo(298.95);
        assertThat(req.terminal().isOnline()).isFalse();
        assertThat(req.terminal().cardPresent()).isTrue();
        assertThat(req.terminal().kmFromHome()).isEqualTo(13.709);
        assertThat(req.lastTransaction()).isNotNull();
        assertThat(req.lastTransaction().timestamp()).isEqualTo("2026-03-11T14:58:35Z");
        assertThat(req.lastTransaction().kmFromCurrent()).isEqualTo(18.862);
    }

    @Test
    void deserializesNullLastTransaction() throws Exception {
        String json = """
                {
                  "id": "tx-1329056812",
                  "transaction": { "amount": 41.12, "installments": 2, "requested_at": "2026-03-11T18:45:53Z" },
                  "customer": { "avg_amount": 82.24, "tx_count_24h": 3, "known_merchants": ["MERC-003", "MERC-016"] },
                  "merchant": { "id": "MERC-016", "mcc": "5411", "avg_amount": 60.25 },
                  "terminal": { "is_online": false, "card_present": true, "km_from_home": 29.23 },
                  "last_transaction": null
                }
                """;

        TransactionRequest req = mapper.readValue(json, TransactionRequest.class);

        assertThat(req.lastTransaction()).isNull();
    }

    @Test
    void deserializesOnlineTerminalWithoutCardPresent() throws Exception {
        String json = """
                {
                  "id": "tx-online",
                  "transaction": { "amount": 100.0, "installments": 1, "requested_at": "2026-03-14T05:15:12Z" },
                  "customer": { "avg_amount": 81.28, "tx_count_24h": 20, "known_merchants": [] },
                  "merchant": { "id": "MERC-068", "mcc": "7802", "avg_amount": 54.86 },
                  "terminal": { "is_online": true, "card_present": false, "km_from_home": 952.27 },
                  "last_transaction": null
                }
                """;

        TransactionRequest req = mapper.readValue(json, TransactionRequest.class);

        assertThat(req.terminal().isOnline()).isTrue();
        assertThat(req.terminal().cardPresent()).isFalse();
    }
}
