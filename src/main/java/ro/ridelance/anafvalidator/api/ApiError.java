package ro.ridelance.anafvalidator.api;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

/** Corpul răspunsurilor de eroare (4xx / 5xx). */
public record ApiError(int status, String error, String message, String correlationId) {

    public static ApiError of(HttpStatus status, String message) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
