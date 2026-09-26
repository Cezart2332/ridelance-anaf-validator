package ro.ridelance.anafvalidator.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import ro.ridelance.anafvalidator.config.WorkspaceProperties;

/**
 * Un director {@code {root}/{uuid}/} per cerere. Se șterge la închiderea {@link Workspace}; la pornire se
 * curăță resturile rămase de la un proces oprit brusc.
 */
@Component
public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final Path root;
    private final Duration staleAfter;

    public WorkspaceManager(WorkspaceProperties properties) {
        this.root = properties.path().toAbsolutePath().normalize();
        this.staleAfter = Duration.ofMinutes(properties.staleAfterMinutes());
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
        cleanupStale(Instant.now().minus(staleAfter));
    }

    public Workspace create() {
        try {
            return new Workspace(Files.createDirectory(root.resolve(UUID.randomUUID().toString())), this);
        } catch (IOException e) {
            throw new UncheckedIOException("Nu pot crea directorul temporar în " + root, e);
        }
    }

    void delete(Path dir) {
        try {
            deleteRecursively(dir);
        } catch (IOException e) {
            log.warn("Nu am putut șterge directorul temporar {}", dir, e);
        }
    }

    /** Șterge subdirectoarele din rădăcină modificate ultima oară înainte de {@code cutoff}. */
    int cleanupStale(Instant cutoff) throws IOException {
        int removed = 0;
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                if (Files.getLastModifiedTime(child).toInstant().isBefore(cutoff)) {
                    delete(child);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.info("Am șters {} directoare temporare rămase din rulări anterioare", removed);
        }
        return removed;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
