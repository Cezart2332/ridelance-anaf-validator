package ro.ridelance.anafvalidator.core;

import java.util.List;

/**
 * @param rawOutput conținutul brut al fișierului de rezultat DUKIntegrator
 * @param pdf       PDF-ul cu XML-ul atașat; doar la {@code VALIDATE_AND_PDF} și {@code valid}
 */
public record ValidationOutcome(
        boolean valid,
        List<ValidationMessage> errors,
        List<ValidationMessage> warnings,
        String rawOutput,
        byte[] pdf,
        long durationMs) {
}
