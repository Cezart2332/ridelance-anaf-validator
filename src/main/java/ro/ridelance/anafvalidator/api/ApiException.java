package ro.ridelance.anafvalidator.api;

import org.springframework.http.HttpStatus;

/** Cerere respinsă cu un status anume (400, 404 …) și un mesaj pentru backend-ul .NET. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
