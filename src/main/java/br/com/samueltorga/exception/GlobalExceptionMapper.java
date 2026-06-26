package br.com.samueltorga.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Application-wide exception mappers.
 */
public final class GlobalExceptionMapper {

    private GlobalExceptionMapper() { /* utility container */ }

    /**
     * Maps Jackson deserialization failures (malformed or unmappable request body) to 400.
     */
    @Provider
    public static class JsonMapper implements ExceptionMapper<JsonProcessingException> {

        /**
         * Converts a Jackson parsing or mapping exception to 400 Bad Request.
         *
         * @param ex the deserialization exception
         * @return 400 Bad Request response
         */
        @Override
        public Response toResponse(JsonProcessingException ex) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}
