package ro.ridelance.anafvalidator.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import ro.ridelance.anafvalidator.core.MalformedXmlException;
import ro.ridelance.anafvalidator.core.RunnerTimeoutException;

/** Codurile de eroare din contract: 400, 404, 413, 422, 504, 500 (cu correlationId). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return respond(e.status(), e.getMessage());
    }

    @ExceptionHandler(MalformedXmlException.class)
    ResponseEntity<ApiError> malformed(MalformedXmlException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(RunnerTimeoutException.class)
    ResponseEntity<ApiError> timeout(RunnerTimeoutException e) {
        log.warn("Timeout DUKIntegrator: {}", e.getMessage());
        return respond(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "Fișierul depășește dimensiunea maximă permisă");
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> multipart(MultipartException e) {
        return respond(HttpStatus.BAD_REQUEST, "Cerere multipart invalidă: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e) {
        // Excepțiile Spring MVC (parametru lipsă, metodă greșită, 404 …) își știu singure statusul.
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            String detail = errorResponse.getBody().getDetail();
            return respond(status, detail != null ? detail : status.getReasonPhrase());
        }
        log.error("Eroare internă", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Eroare internă");
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status, message));
    }
}
