package ro.ridelance.anafvalidator.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ro.ridelance.anafvalidator.api.dto.ValidatorInfo;
import ro.ridelance.anafvalidator.core.ValidatorRegistry;

@RestController
@RequestMapping("/v1/validators")
public class ValidatorsController {

    private final ValidatorRegistry registry;

    public ValidatorsController(ValidatorRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ValidatorInfo> list() {
        return registry.list().stream().map(ValidatorInfo::from).toList();
    }

    /** Rescanează {@code validators/} după ce s-a copiat un kit nou; nu cere redeploy. */
    @PostMapping("/reload")
    public List<ValidatorInfo> reload() {
        return registry.reload().stream().map(ValidatorInfo::from).toList();
    }
}
