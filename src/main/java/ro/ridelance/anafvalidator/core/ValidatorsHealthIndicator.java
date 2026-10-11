package ro.ridelance.anafvalidator.core;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Serviciul e sănătos doar dacă are cel puțin o versiune de validator instalată. */
@Component("validators")
public class ValidatorsHealthIndicator implements HealthIndicator {

    private final ValidatorRegistry registry;

    public ValidatorsHealthIndicator(ValidatorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        int installed = registry.list().size();
        Health.Builder builder = installed > 0 ? Health.up() : Health.down();
        return builder.withDetail("installedVersions", installed).build();
    }
}
