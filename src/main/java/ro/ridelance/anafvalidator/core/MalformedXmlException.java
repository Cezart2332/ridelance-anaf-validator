package ro.ridelance.anafvalidator.core;

/** Fișierul nu e XML bine format; nu se mai apelează DUKIntegrator. API-ul răspunde 422. */
public class MalformedXmlException extends RuntimeException {

    public MalformedXmlException(String message) {
        super(message);
    }
}
