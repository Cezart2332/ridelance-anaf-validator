package ro.ridelance.anafvalidator.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Verificare XML → DUKIntegrator → parsare rezultat → PDF. Nu păstrează nimic după răspuns. */
@Service
public class ValidationService {

    static final String PDF_MISSING = "PDF_LIPSA";

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final XmlWellFormednessChecker xmlChecker;
    private final WorkspaceManager workspaces;
    private final DukIntegratorRunner runner;
    private final ErrorOutputParser parser;

    public ValidationService(XmlWellFormednessChecker xmlChecker, WorkspaceManager workspaces,
            DukIntegratorRunner runner, ErrorOutputParser parser) {
        this.xmlChecker = xmlChecker;
        this.workspaces = workspaces;
        this.runner = runner;
        this.parser = parser;
    }

    public ValidationOutcome validate(ValidatorKit kit, DeclarationType type, ValidationMode mode, byte[] xml) {
        long start = System.nanoTime();
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
