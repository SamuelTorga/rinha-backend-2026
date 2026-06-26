package br.com.samueltorga.resource;

import br.com.samueltorga.dataset.DatasetLoader;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class FraudScoreResourceTest {

    @InjectMock
    DatasetLoader datasetLoader;

    private static final String LEGIT_PAYLOAD = """
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
    void returns503WhenDatasetIsNotReady() {
        Mockito.when(datasetLoader.isReady()).thenReturn(false);

        given()
                .contentType(ContentType.JSON)
                .body(LEGIT_PAYLOAD)
                .when().post("/fraud-score")
                .then().statusCode(503);
    }

    @Test
    void returns200WithBodyWhenDatasetIsReady() {
        Mockito.when(datasetLoader.isReady()).thenReturn(true);

        given()
                .contentType(ContentType.JSON)
                .body(LEGIT_PAYLOAD)
                .when().post("/fraud-score")
                .then()
                .statusCode(200)
                .body("approved", notNullValue())
                .body("fraud_score", notNullValue());
    }
}
