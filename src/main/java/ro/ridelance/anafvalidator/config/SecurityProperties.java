package ro.ridelance.anafvalidator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tokenul partajat cu backend-ul .NET, trimis în header-ul {@code X-Internal-Token}. */
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(String internalToken) {
}
