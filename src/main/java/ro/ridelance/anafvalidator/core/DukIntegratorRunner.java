package ro.ridelance.anafvalidator.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ro.ridelance.anafvalidator.config.RunnerProperties;

/**
 * Rulează DUKIntegrator în mod linie de comandă, într-un proces JVM separat per cerere.
 * Comanda urmează {@code docs/DUKINTEGRATOR_NOTES.md}; argumentele sunt o listă (fără shell), iar singurele
 * valori venite din cerere sunt tipul declarației (enum) și versiunea kitului (deja găsită în registru).
 */
@Component
public class DukIntegratorRunner {

    private static final Logger log = LoggerFactory.getLogger(DukIntegratorRunner.class);

    private final RunnerProperties properties;
    private final Semaphore permits;
    private final String javaCommand;
    private final int maxOutputBytes;

    public DukIntegratorRunner(RunnerProperties properties) {
        this.properties = properties;
        this.permits = new Semaphore(properties.maxConcurrent(), true);
        this.javaCommand = properties.javaCommand() == null || properties.javaCommand().isBlank()
                ? Path.of(System.getProperty("java.home"), "bin", "java").toString()
                : properties.javaCommand();
        this.maxOutputBytes = properties.maxOutputKb() * 1024;
    }

    /**
     * @throws RunnerTimeoutException dacă nu se eliberează un loc sau procesul nu termină în {@code timeoutSeconds}
     */
    public DukRunResult run(ValidatorKit kit, DeclarationType type, ValidationMode mode, Workspace workspace) {
        int timeout = properties.timeoutSeconds();
        boolean acquired;
        try {
            acquired = permits.tryAcquire(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Întrerupt în așteptarea unui loc liber pentru DUKIntegrator", e);
        }
        if (!acquired) {
            throw new RunnerTimeoutException("Niciun loc liber pentru DUKIntegrator în " + timeout + " s");
        }
        try {
            return execute(command(kit, type, mode, workspace), workspace, timeout);
        } finally {
            permits.release();
        }
    }

    List<String> command(ValidatorKit kit, DeclarationType type, ValidationMode mode, Workspace workspace) {
        List<String> command = new ArrayList<>();
        command.add(javaCommand);
        command.addAll(properties.jvmArgs());
        command.add("-jar");
        command.add(kit.integratorJar().toString());
        command.add(mode == ValidationMode.VALIDATE_AND_PDF ? "-p" : "-v");
        command.add(type.name());
        command.add(workspace.xml().toString());
        // '+' păstrează atenționările în fișierul de rezultat și la -p (adaugă 'ok' la final).
        command.add("+" + workspace.result());
        if (mode == ValidationMode.VALIDATE_AND_PDF) {
            command.add("0"); // optiuneValidare
            command.add("0"); // fisierZIP: declarațiile noastre nu au ZIP atașat
            command.add(workspace.pdf().toString());
        }
        return command;
    }

    private DukRunResult execute(List<String> command, Workspace workspace, int timeoutSeconds) {
        long start = System.nanoTime();
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(workspace.dir().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(workspace.stdout().toFile())
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException("Nu pot porni DUKIntegrator", e);
        }
        try {
            process.getOutputStream().close();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                kill(process);
                throw new RunnerTimeoutException("DUKIntegrator nu a terminat în " + timeoutSeconds + " s");
            }
        } catch (InterruptedException e) {
            kill(process);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Întrerupt în timpul rulării DUKIntegrator", e);
        } catch (IOException e) {
            kill(process);
            throw new UncheckedIOException(e);
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        Path pdf = workspace.pdf();
        return new DukRunResult(
                process.exitValue(),
                readLimited(workspace.result()),
                readLimited(workspace.stdout()),
                Files.isRegularFile(pdf) ? pdf : null,
                durationMs);
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                log.warn("Procesul DUKIntegrator {} nu s-a oprit după kill", process.pid());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Conținutul fișierului, trunchiat la {@code maxOutputKb}; {@code null} dacă fișierul nu există. */
    private String readLimited(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(maxOutputBytes + 1);
            if (bytes.length <= maxOutputBytes) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            return new String(bytes, 0, maxOutputBytes, StandardCharsets.UTF_8) + "\n…[trunchiat]";
        } catch (IOException e) {
            throw new UncheckedIOException("Nu pot citi " + file.getFileName(), e);
        }
    }
}
