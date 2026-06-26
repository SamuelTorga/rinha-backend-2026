package br.com.samueltorga.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FraudScoreResponseTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Test
    void serializesApprovedWithZeroScore() throws Exception {
        JsonNode node = mapper.valueToTree(new FraudScoreResponse(true, 0.0f));

        assertTrue(node.get("approved").asBoolean());
        assertEquals(0.0f, node.get("fraud_score").floatValue());
        assertFalse(node.has("fraudScore"), "camelCase key must not appear in JSON");
    }

    @Test
    void serializesRejectedWithFullScore() throws Exception {
        JsonNode node = mapper.valueToTree(new FraudScoreResponse(false, 1.0f));

        assertFalse(node.get("approved").asBoolean());
        assertEquals(1.0f, node.get("fraud_score").floatValue());
    }

    @Test
    void serializesPartialScore() throws Exception {
        JsonNode node = mapper.valueToTree(new FraudScoreResponse(true, 0.4f));

        assertEquals(0.4f, node.get("fraud_score").floatValue(), 0.001f);
    }
}
