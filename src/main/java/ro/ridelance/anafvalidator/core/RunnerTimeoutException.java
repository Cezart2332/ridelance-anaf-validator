package ro.ridelance.anafvalidator.core;

/** DUKIntegrator nu a terminat la timp (sau nu s-a eliberat un loc); API-ul răspunde 504. */
public class RunnerTimeoutException extends RuntimeException {

    public RunnerTimeoutException(String message) {
        super(message);
    }
}
