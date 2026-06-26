package br.com.samueltorga.resource;

import br.com.samueltorga.dataset.DatasetLoader;
import br.com.samueltorga.dto.FraudScoreResponse;
import br.com.samueltorga.dto.TransactionRequest;
import br.com.samueltorga.service.FraudDetectionService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/fraud-score")
public class FraudScoreResource {

    @Inject
    DatasetLoader datasetLoader;

    @Inject
    FraudDetectionService detectionService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RunOnVirtualThread
    public Response fraudScore(TransactionRequest request) {
        if (!datasetLoader.isReady()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        FraudScoreResponse result = detectionService.evaluate(request);
        return Response.ok(result).build();
    }
}
