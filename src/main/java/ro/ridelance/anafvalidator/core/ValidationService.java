package ro.ridelance.anafvalidator.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Verificare XML → DUKIntegrator → parsare rezultat → PDF. Nu păstrează nimic după răspuns.
 * Fiecare validare lasă un log structurat și metrica {@code anaf.validation};
 * conținutul XML nu se loghează niciodată (are date fiscale).
 */
@Service
public class ValidationService {

    static final String PDF_MISSING = "PDF_LIPSA";

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final XmlWellFormednessChecker xmlChecker;
    private final WorkspaceManager workspaces;
    private final DukIntegratorRunner runner;
    private final ErrorOutputParser parser;
    private final MeterRegistry meters;

    public ValidationService(XmlWellFormednessChecker xmlChecker, WorkspaceManager workspaces,
            DukIntegratorRunner runner, ErrorOutputParser parser, MeterRegistry meters) {
        this.xmlChecker = xmlChecker;
        this.workspaces = workspaces;
        this.runner = runner;
        this.parser = parser;
        this.meters = meters;
    }

    public ValidationOutcome validate(ValidatorKit kit, DeclarationType type, ValidationMode mode, byte[] xml) {
        long start = System.nanoTime();
        String outcome = "error";
        Boolean valid = null;
        try {
            ValidationOutcome result = validateInWorkspace(kit, type, mode, xml, start);
            valid = result.valid();
            outcome = valid ? "valid" : "invalid";
            return result;
        } catch (MalformedXmlException e) {
            outcome = "malformed";
            throw e;
        } catch (RunnerTimeoutException e) {
            outcome = "timeout";
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            // Timer-ul dă și numărul de validări; outcome=timeout le numără pe cele expirate.
            Timer.builder("anaf.validation")
                    .tags("declarationType", type.name(), "mode", mode.name(), "outcome", outcome)
                    .register(meters)
                    .record(Duration.ofMillis(durationMs));
            // correlationId, declarationType și validatorVersion vin din MDC (puse de controller).
            log.atInfo()
                    .addKeyValue("mode", mode.name())
                    .addKeyValue("outcome", outcome)
                    .addKeyValue("valid", valid)
                    .addKeyValue("durationMs", durationMs)
                    .log("Validare {} {} {}: {}", type, kit.version(), mode, outcome);
        }
    }

    private ValidationOutcome validateInWorkspace(ValidatorKit kit, DeclarationType type, ValidationMode mode,
            byte[] xml, long start) {
        xmlChecker.check(xml);

        try (Workspace workspace = workspaces.create()) {
            Files.write(workspace.xml(), xml);
            DukRunResult run = runner.run(kit, type, mode, workspace);
            if (run.resultOutput() == null) {
                // Nu ar trebui să se întâmple (tipul și fișierul sunt verificate înainte); stdout spune de ce.
                log.error("DUKIntegrator nu a creat fișierul de rezultat. exitCode={} stdout={}",
                        run.exitCode(), run.stdout());
                throw new IllegalStateException("DUKIntegrator nu a produs fișierul de rezultat");
            }

            ErrorOutputParser.ParsedOutput parsed = parser.parse(run.resultOutput());
            List<ValidationMessage> errors = new ArrayList<>(parsed.errors());
            boolean valid = parsed.valid();
            byte[] pdf = null;
            if (mode == ValidationMode.VALIDATE_AND_PDF && valid) {
                if (run.pdf() == null) {
                    errors.add(new ValidationMessage(PDF_MISSING, "DUKIntegrator nu a generat PDF-ul", null, null));
                    valid = false;
                } else {
                    pdf = Files.readAllBytes(run.pdf());
                }
            }
            return new ValidationOutcome(valid, List.copyOf(errors), parsed.warnings(), run.resultOutput(), pdf,
                    (System.nanoTime() - start) / 1_000_000);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
