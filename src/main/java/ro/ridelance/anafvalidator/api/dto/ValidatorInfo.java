package ro.ridelance.anafvalidator.api.dto;

import java.time.Instant;
import java.util.List;

import ro.ridelance.anafvalidator.core.DeclarationType;
import ro.ridelance.anafvalidator.core.ValidatorKit;

public record ValidatorInfo(String version, List<DeclarationType> declarations, Instant installedAt) {

    public static ValidatorInfo from(ValidatorKit kit) {
        return new ValidatorInfo(kit.version(), kit.declarations().stream().sorted().toList(), kit.installedAt());
    }
}
