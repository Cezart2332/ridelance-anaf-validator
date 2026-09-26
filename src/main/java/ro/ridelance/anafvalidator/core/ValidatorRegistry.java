package ro.ridelance.anafvalidator.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import ro.ridelance.anafvalidator.config.ValidatorsProperties;

/**
 * Scanează {@code validators/}: fiecare subfolder cu {@code DUKIntegrator.jar} e o versiune de kit.
 * Se rescanează la pornire și la {@code POST /v1/validators/reload}, deci un kit nou nu cere redeploy.
 */
@Component
public class ValidatorRegistry {

    /** Numele folderului de versiune ajunge în căi și în răspuns, deci e restricționat. */
    public static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    static final String INTEGRATOR_JAR = "DUKIntegrator.jar";

    private static final Logger log = LoggerFactory.getLogger(ValidatorRegistry.class);

    private final Path root;
    private volatile Map<String, ValidatorKit> kits = Map.of();

    public ValidatorRegistry(ValidatorsProperties properties) {
        this.root = properties.path().toAbsolutePath().normalize();
    }

    @PostConstruct
    public synchronized List<ValidatorKit> reload() {
        Map<String, ValidatorKit> found = new LinkedHashMap<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> children = Files.list(root)) {
                children.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(dir -> scan(dir).ifPresent(kit -> found.put(kit.version(), kit)));
            } catch (IOException e) {
                log.error("Nu pot citi folderul de validatoare {}", root, e);
            }
        } else {
            log.warn("Folderul de validatoare {} nu există", root);
        }
        kits = Map.copyOf(found);
        log.info("Versiuni de validator instalate: {}", found.keySet());
        return list();
    }

    public List<ValidatorKit> list() {
        return kits.values().stream().sorted(Comparator.comparing(ValidatorKit::version)).toList();
    }

    public Optional<ValidatorKit> find(String version) {
        return Optional.ofNullable(kits.get(version));
    }

    public Path root() {
        return root;
    }

    private Optional<ValidatorKit> scan(Path dir) {
        String version = dir.getFileName().toString();
        Path jar = dir.resolve(INTEGRATOR_JAR);
        if (!Files.isRegularFile(jar)) {
            return Optional.empty();
        }
        if (!VERSION.matcher(version).matches()) {
            log.warn("Ignor kitul din {}: numele versiunii nu e permis", dir);
            return Optional.empty();
        }
        Path lib = dir.resolve("lib");
        EnumSet<DeclarationType> declarations = EnumSet.noneOf(DeclarationType.class);
        EnumSet<DeclarationType> pdf = EnumSet.noneOf(DeclarationType.class);
        for (DeclarationType type : DeclarationType.values()) {
            if (Files.isRegularFile(lib.resolve(type.validatorJar()))) {
                declarations.add(type);
                if (Files.isRegularFile(lib.resolve(type.pdfJar()))) {
                    pdf.add(type);
                }
            }
        }
        Set<DeclarationType> missing = EnumSet.complementOf(declarations);
        if (!missing.isEmpty()) {
            log.warn("Kitul {} nu are validatoare pentru {}", version, missing);
        }
        try {
            return Optional.of(new ValidatorKit(version, dir, jar, Set.copyOf(declarations), Set.copyOf(pdf),
                    Files.getLastModifiedTime(dir).toInstant()));
        } catch (IOException e) {
            log.error("Nu pot citi kitul {}", dir, e);
            return Optional.empty();
        }
    }
}
