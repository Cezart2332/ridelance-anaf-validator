package ro.ridelance.anafvalidator.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Folderul cu kiturile DUKIntegrator, câte un subfolder per versiune (ex. {@code validators/2026-09}). */
@ConfigurationProperties(prefix = "validators")
public record ValidatorsProperties(Path path) {
}
