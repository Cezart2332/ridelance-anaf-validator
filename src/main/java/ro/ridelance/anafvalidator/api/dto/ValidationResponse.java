package ro.ridelance.anafvalidator.api.dto;

import java.util.Base64;
import java.util.List;

import ro.ridelance.anafvalidator.core.DeclarationType;
import ro.ridelance.anafvalidator.core.ValidationMessage;
import ro.ridelance.anafvalidator.core.ValidationOutcome;

/** Răspunsul {@code 200} de la {@code POST /v1/validate}; un XML invalid e tot {@code 200}, cu {@code valid=false}. */
public record ValidationResponse(
        boolean valid,
        DeclarationType declarationType,
        String validatorVersion,
        List<ValidationMessage> errors,
        List<ValidationMessage> warnings,
        String rawOutput,
        String pdfBase64,
        long durationMs,
        String correlationId) {

    public static ValidationResponse from(ValidationOutcome outcome, DeclarationType type, String version,
            String correlationId) {
        return new ValidationResponse(
                outcome.valid(),
                type,
                version,
                outcome.errors(),
                outcome.warnings(),
                outcome.rawOutput(),
                outcome.pdf() == null ? null : Base64.getEncoder().encodeToString(outcome.pdf()),
                outcome.durationMs(),
                correlationId);
    }
}
