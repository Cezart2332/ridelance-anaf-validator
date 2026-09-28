package ro.ridelance.anafvalidator.api;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import ro.ridelance.anafvalidator.api.dto.ValidationRequest;
import ro.ridelance.anafvalidator.api.dto.ValidationResponse;
import ro.ridelance.anafvalidator.core.DeclarationType;
import ro.ridelance.anafvalidator.core.ValidationMode;
import ro.ridelance.anafvalidator.core.ValidationOutcome;
import ro.ridelance.anafvalidator.core.ValidationService;
import ro.ridelance.anafvalidator.core.ValidatorKit;
import ro.ridelance.anafvalidator.core.ValidatorRegistry;

@RestController
public class ValidationController {

    private final ValidatorRegistry registry;
    private final ValidationService validationService;

    public ValidationController(ValidatorRegistry registry, ValidationService validationService) {
        this.registry = registry;
        this.validationService = validationService;
    }

    @PostMapping(path = "/v1/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ValidationResponse validate(@ModelAttribute ValidationRequest request, HttpServletResponse response) {
        String correlationId = request.correlationId();
        if (correlationId != null && !correlationId.isEmpty()) {
            if (!CorrelationIdFilter.VALID.matcher(correlationId).matches()) {
                throw badRequest("correlationId trebuie să aibă 1–100 caractere din [A-Za-z0-9._:-]");
            }
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            response.setHeader(CorrelationIdFilter.HEADER, correlationId);
        }

        DeclarationType type = DeclarationType.parse(request.declarationType())
                .orElseThrow(() -> badRequest("declarationType trebuie să fie D100, D301, D390 sau D700"));
        ValidationMode mode = ValidationMode.parse(request.mode())
                .orElseThrow(() -> badRequest("mode trebuie să fie VALIDATE sau VALIDATE_AND_PDF"));
        String version = request.validatorVersion();
        if (version == null || !ValidatorRegistry.VERSION.matcher(version).matches()) {
            throw badRequest("validatorVersion lipsă sau invalid");
        }
        if (request.xml() == null || request.xml().isEmpty()) {
            throw badRequest("Fișierul xml lipsește sau e gol");
        }
        MDC.put("declarationType", type.name());
        MDC.put("validatorVersion", version);

        ValidatorKit kit = registry.find(version)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "validatorVersion necunoscut: " + version));
        if (!kit.supports(type)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Kitul " + version + " nu are validator pentru " + type);
        }
        if (mode == ValidationMode.VALIDATE_AND_PDF && !kit.supportsPdf(type)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Kitul " + version + " nu poate genera PDF pentru " + type);
        }

        byte[] xml;
        try {
            xml = request.xml().getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ValidationOutcome outcome = validationService.validate(kit, type, mode, xml);
        return ValidationResponse.from(outcome, type, version, MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
