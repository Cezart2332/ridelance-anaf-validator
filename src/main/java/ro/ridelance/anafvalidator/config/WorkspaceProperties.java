package ro.ridelance.anafvalidator.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Directoarele temporare per cerere.
 *
 * @param path              rădăcina sub care se creează {@code {uuid}/} pentru fiecare cerere
 * @param staleAfterMinutes la pornire se șterg resturile mai vechi de atât
 */
@ConfigurationProperties(prefix = "workspace")
public record WorkspaceProperties(Path path, int staleAfterMinutes) {
}
