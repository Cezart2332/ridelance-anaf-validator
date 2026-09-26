package ro.ridelance.anafvalidator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Găsește un kit DUKIntegrator real pentru testele de integrare: {@code $VALIDATORS_PATH} sau {@code ./validators}.
 * Kitul nu e în git, deci testele care îl cer se sar când lipsește.
 */
public final class KitSupport {

    private KitSupport() {
    }

    public static Path validatorsRoot() {
        String env = System.getenv("VALIDATORS_PATH");
        return Path.of(env == null || env.isBlank() ? "validators" : env).toAbsolutePath().normalize();
    }

    public static Optional<Path> kitDirectory() {
        Path root = validatorsRoot();
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(dir -> Files.isRegularFile(dir.resolve("DUKIntegrator.jar"))).sorted().findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Folosit de {@code @EnabledIf}. */
    public static boolean kitAvailable() {
        return kitDirectory().isPresent();
    }

    public static byte[] fixture(String name) {
        try (var in = KitSupport.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture lipsă: " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String sample(String name) {
        try (var in = KitSupport.class.getResourceAsStream("/samples/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Mostră lipsă: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
