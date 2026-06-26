package br.com.samueltorga.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import br.com.samueltorga.dataset.DatasetLoader;
import br.com.samueltorga.dto.FraudScoreResponse;
import br.com.samueltorga.dto.TransactionRequest;
import br.com.samueltorga.service.FraudDetectionService;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class FraudScoreResourceTest {

    @InjectMock
    DatasetLoader datasetLoader;

    @InjectMock
    FraudDetectionService detectionService;

    private static final String VALID_PAYLOAD = """
            {
              "id": "tx-1329056812",
              "transaction": { "amount": 41.12, "installments": 2, "requested_at": "2026-03-11T18:45:53Z" },
              "customer": { "avg_amount": 82.24, "tx_count_24h": 3, "known_merchants": ["MERC-003", "MERC-016"] },
              "merchant": { "id": "MERC-016", "mcc": "5411", "avg_amount": 60.25 },
              "terminal": { "is_online": false, "card_present": true, "km_from_home": 29.23 },
              "last_transaction": null
            }
            """;

    @Test
    void returns503WhenDatasetNotReady() {
        when(datasetLoader.isReady()).thenReturn(false);

        given()
                .contentType(ContentType.JSON).body(VALID_PAYLOAD)
                .when().post("/fraud-score")
                .then().statusCode(503);

        verifyNoInteractions(detectionService);
    }

    @Test
    void returnsApprovedWhenFraudScoreIsLow() {
        when(datasetLoader.isReady()).thenReturn(true);
        when(detectionService.evaluate(any(TransactionRequest.class)))
                .thenReturn(new FraudScoreResponse(true, 0.0f));

        given()
                .contentType(ContentType.JSON).body(VALID_PAYLOAD)
                .when().post("/fraud-score")
                .then()
                .statusCode(200)
                .body("approved", equalTo(true))
                .body("fraud_score", equalTo(0.0f));
    }

    @Test
    void returnsRejectedWhenFraudScoreIsHigh() {
        when(datasetLoader.isReady()).thenReturn(true);
        when(detectionService.evaluate(any(TransactionRequest.class)))
                .thenReturn(new FraudScoreResponse(false, 1.0f));

        given()
                .contentType(ContentType.JSON).body(VALID_PAYLOAD)
                .when().post("/fraud-score")
                .then()
                .statusCode(200)
                .body("approved", equalTo(false))
                .body("fraud_score", equalTo(1.0f));
    }

    @Test
    void returns400ForMalformedJson() {
        given()
                .contentType(ContentType.JSON)
                .body("{ not valid json }")
                .when().post("/fraud-score")
                .then()
                .statusCode(400);
    }

}
