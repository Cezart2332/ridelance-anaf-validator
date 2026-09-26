package ro.ridelance.anafvalidator.core;

import java.util.Arrays;
import java.util.Optional;

public enum ValidationMode {
    /** DUKIntegrator {@code -v}. */
    VALIDATE,
    /** DUKIntegrator {@code -p}: validare + PDF cu XML-ul atașat. */
    VALIDATE_AND_PDF;

    public static Optional<ValidationMode> parse(String value) {
        return Arrays.stream(values()).filter(m -> m.name().equals(value)).findFirst();
    }
}
