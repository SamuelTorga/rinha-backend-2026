package br.com.samueltorga.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        assertEquals("tx-3576980410", req.id());
        assertEquals(384.88, req.transaction().amount());
        assertEquals(3, req.transaction().installments());
        assertEquals("2026-03-11T20:23:35Z", req.transaction().requestedAt());
        assertEquals(769.76, req.customer().avgAmount());
        assertEquals(3, req.customer().txCount24h());
        assertEquals(List.of("MERC-009", "MERC-001"), req.customer().knownMerchants());
        assertEquals("MERC-001", req.merchant().id());
        assertEquals("5912", req.merchant().mcc());
        assertEquals(298.95, req.merchant().avgAmount());
        assertFalse(req.terminal().isOnline());
        assertTrue(req.terminal().cardPresent());
        assertEquals(13.709, req.terminal().kmFromHome());
        assertNotNull(req.lastTransaction());
        assertEquals("2026-03-11T14:58:35Z", req.lastTransaction().timestamp());
        assertEquals(18.862, req.lastTransaction().kmFromCurrent());
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

        assertNull(req.lastTransaction());
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

        assertTrue(req.terminal().isOnline());
        assertFalse(req.terminal().cardPresent());
    }
}
