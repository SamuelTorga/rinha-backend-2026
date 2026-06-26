package br.com.samueltorga.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import br.com.samueltorga.dataset.DatasetLoader;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ReadyResourceTest {

    @InjectMock
    DatasetLoader datasetLoader;

    @Test
    void returns200WhenDatasetIsReady() {
        Mockito.when(datasetLoader.isReady()).thenReturn(true);

        given().when().get("/ready").then().statusCode(200);
    }

    @Test
    void returns503WhenDatasetIsNotReady() {
        Mockito.when(datasetLoader.isReady()).thenReturn(false);

        given().when().get("/ready").then().statusCode(503);
    }
}
