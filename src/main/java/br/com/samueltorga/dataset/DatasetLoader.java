package br.com.samueltorga.dataset;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Startup
@ApplicationScoped
public class DatasetLoader {

    private static final Logger LOG = Logger.getLogger(DatasetLoader.class);
    private static final int INITIAL_CAPACITY = 200_000;

    @Inject
    ObjectMapper objectMapper;

    private float[] vectors;
    private boolean[] isfraud;
    private int vectorCount;
    private Map<String, Float> mccRisk;
    private NormalizationConstants normalization;

    // volatile write is the publication fence for all fields above
    private volatile boolean ready = false;

    @PostConstruct
    void init() {
        Thread.startVirtualThread(() -> {
            try {
                long start = System.currentTimeMillis();
                loadNormalization();
                loadMccRisk();
                loadReferences();
                this.ready = true;
                LOG.infof("Dataset ready: %d vectors in %d ms", vectorCount, System.currentTimeMillis() - start);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to load dataset — API will stay unavailable");
            }
        });
    }

    private void loadNormalization() throws IOException {
        try (InputStream is = openResource("data/normalization.json")) {
            normalization = objectMapper.readValue(is, NormalizationConstants.class);
        }
        LOG.info("Normalization constants loaded");
    }

    private void loadMccRisk() throws IOException {
        try (InputStream is = openResource("data/mcc_risk.json")) {
            mccRisk = objectMapper.readValue(is, new TypeReference<Map<String, Float>>() {});
        }
        LOG.infof("MCC risk map loaded: %d entries", mccRisk.size());
    }

    private void loadReferences() throws IOException {
        float[] tempVectors = new float[INITIAL_CAPACITY * 14];
        boolean[] tempLabels = new boolean[INITIAL_CAPACITY];
        int count = 0;

        try (InputStream gz = new GZIPInputStream(openResource("data/references.json.gz"));
             JsonParser parser = objectMapper.getFactory().createParser(gz)) {

            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("references.json.gz: expected a JSON array at root");
            }

            while (parser.nextToken() == JsonToken.START_OBJECT) {
                if (count == tempVectors.length / 14) {
                    tempVectors = Arrays.copyOf(tempVectors, tempVectors.length * 2);
                    tempLabels = Arrays.copyOf(tempLabels, tempLabels.length * 2);
                }

                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String field = parser.currentName();
                    parser.nextToken();
                    if ("vector".equals(field)) {
                        int base = count * 14;
                        int dim = 0;
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            tempVectors[base + dim++] = parser.getFloatValue();
                        }
                    } else if ("label".equals(field)) {
                        tempLabels[count] = "fraud".equals(parser.getText());
                    }
                }
                count++;
            }
        }

        vectorCount = count;
        vectors = (count * 14 == tempVectors.length) ? tempVectors : Arrays.copyOf(tempVectors, count * 14);
        isfraud = (count == tempLabels.length) ? tempLabels : Arrays.copyOf(tempLabels, count);
        LOG.infof("References loaded: %d vectors", count);
    }

    private InputStream openResource(String name) {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        if (is == null) {
            throw new RuntimeException("Resource not found on classpath: " + name
                    + " — place data files under src/main/resources/data/");
        }
        return is;
    }

    public boolean isReady() {
        return ready;
    }

    public int vectorCount() {
        return vectorCount;
    }

    public float[] vectors() {
        return vectors;
    }

    public boolean[] isfraud() {
        return isfraud;
    }

    public Map<String, Float> mccRisk() {
        return mccRisk;
    }

    public NormalizationConstants normalization() {
        return normalization;
    }
}
