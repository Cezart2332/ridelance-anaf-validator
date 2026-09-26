package ro.ridelance.anafvalidator.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

/**
 * O versiune instalată a kitului: {@code validators/{version}/DUKIntegrator.jar} + {@code lib/}.
 *
 * @param declarations    declarațiile pentru care există {@code lib/DxxxValidator.jar}
 * @param pdfDeclarations declarațiile pentru care există și {@code lib/DxxxPdf.jar}
 */
public record ValidatorKit(
        String version,
        Path directory,
        Path integratorJar,
        Set<DeclarationType> declarations,
        Set<DeclarationType> pdfDeclarations,
        Instant installedAt) {

    public boolean supports(DeclarationType type) {
        return declarations.contains(type);
    }

    public boolean supportsPdf(DeclarationType type) {
        return pdfDeclarations.contains(type);
    }
}
