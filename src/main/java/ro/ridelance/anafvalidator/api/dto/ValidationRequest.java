package ro.ridelance.anafvalidator.api.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * Câmpurile formularului {@code multipart/form-data} de la {@code POST /v1/validate}.
 * Valorile se validează strict în controller (whitelist), fiindcă ajung în argumentele DUKIntegrator.
 */
public record ValidationRequest(
        MultipartFile xml,
        String declarationType,
        String validatorVersion,
        String mode,
        String correlationId) {
}
