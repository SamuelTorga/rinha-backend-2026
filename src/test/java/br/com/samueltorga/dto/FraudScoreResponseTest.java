package br.com.samueltorga.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FraudScoreResponseTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Test
    void serializesApprovedWithZeroScore() throws Exception {
        JsonNode node = mapper.valueToTree(new FraudScoreResponse(true, 0.0f));

        assertThat(node.get("approved").asBoolean()).isTrue();
        assertThat(node.get("fraud_score").floatValue()).isEqualTo(0.0f);
        assertThat(node.has("fraudScore")).as("camelCase key must not appear in JSON").isFalse();
    }

    @Test
    void serializesRejectedWithFullScore() throws Exception {
        JsonNode node = mapper.valueToTree(new FraudScoreResponse(false, 1.0f));

        assertThat(node.get("approved").asBoolean()).isFalse();
        assertThat(node.get("fraud_score").floatValue()).isEqualTo(1.0f);
    }

    @Test
    void serializesPartialScore() throws Exception {
        JsonNode node = mapper.valueToTree(new FraudScoreResponse(true, 0.4f));

        assertThat(node.get("fraud_score").floatValue()).isCloseTo(0.4f, org.assertj.core.data.Offset.offset(0.001f));
    }
}
