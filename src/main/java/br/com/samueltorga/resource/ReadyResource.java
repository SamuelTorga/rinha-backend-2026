package br.com.samueltorga.resource;

import br.com.samueltorga.dataset.DatasetLoader;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/ready")
public class ReadyResource {

    @Inject
    DatasetLoader datasetLoader;

    /**
     * Health-check endpoint for the dataset readiness probe.
     *
     * <p>Returns {@code 200 OK} when the dataset is loaded, {@code 503 Service Unavailable} otherwise.
     */
    @GET
    @RunOnVirtualThread
    public Response ready() {
        return datasetLoader.isReady()
                ? Response.ok().build()
                : Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
    }
}
