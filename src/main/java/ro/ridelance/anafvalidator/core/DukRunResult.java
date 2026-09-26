package ro.ridelance.anafvalidator.core;

import java.nio.file.Path;

/**
 * Ce a lăsat în urmă un proces DUKIntegrator.
 *
 * @param exitCode     mereu 0 în practică; nu decide rezultatul (vezi notele J1)
 * @param resultOutput conținutul {@code fisierRezultat}; {@code null} dacă DUKIntegrator nu l-a creat
 * @param stdout       stdout + stderr
 * @param pdf          PDF-ul generat sau {@code null}
 */
public record DukRunResult(int exitCode, String resultOutput, String stdout, Path pdf, long durationMs) {
}
