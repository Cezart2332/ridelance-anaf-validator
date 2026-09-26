package ro.ridelance.anafvalidator.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rularea DUKIntegrator ca proces JVM separat.
 *
 * @param timeoutSeconds cât poate dura un proces DUKIntegrator (și cât așteaptă o cerere un loc liber)
 * @param maxConcurrent  câte procese DUKIntegrator rulează simultan
 * @param javaCommand    executabilul java pentru procesul copil; gol = java-ul care rulează serviciul
 * @param jvmArgs        argumentele JVM ale procesului copil
 * @param maxOutputKb    cât citim din ieșirea procesului și din fișierul de rezultat
 */
@ConfigurationProperties(prefix = "runner")
public record RunnerProperties(
        int timeoutSeconds,
        int maxConcurrent,
        String javaCommand,
        List<String> jvmArgs,
        int maxOutputKb) {
}
