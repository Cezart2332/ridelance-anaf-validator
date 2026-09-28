package ro.ridelance.anafvalidator.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import ro.ridelance.anafvalidator.KitSupport;
import ro.ridelance.anafvalidator.security.InternalTokenFilter;

/**
 * Serviciul pornit pe un port real, cu kitul DUKIntegrator din {@code validators/}.
 * Testele care au nevoie de kit se sar dacă lipsește; limita de upload se verifică oricum.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.internalToken=test-token")
class RealKitApiTest {

    @Autowired
    TestRestTemplate http;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("validators.path", () -> KitSupport.validatorsRoot().toString());
    }

    @ParameterizedTest
    @CsvSource({"D100, d100-valid.xml", "D301, d301-valid.xml", "D390, d390-valid.xml", "D700, d700-valid.xml"})
    @EnabledIf("ro.ridelance.anafvalidator.KitSupport#kitAvailable")
    void validFixturesProduceAPdf(String type, String fixture) {
        Map<String, Object> body = validate(type, fixture, "VALIDATE_AND_PDF").getBody();

        assertThat(body).containsEntry("valid", true).containsEntry("declarationType", type);
        assertThat((List<?>) body.get("errors")).isEmpty();
        // D700 are mereu atenționarea „urmează să fie prelucrat la organul fiscal competent”.
        assertThat((String) body.get("rawOutput")).endsWith("ok");
        byte[] pdf = Base64.getDecoder().decode((String) body.get("pdfBase64"));
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @ParameterizedTest
    @CsvSource({
            "D100, d100-invalid.xml, R15.1",
            "D301, d301-invalid.xml, R28",
            "D390, d390-invalid.xml, R24.1",
            "D700, d700-invalid.xml, R7.2"})
    @EnabledIf("ro.ridelance.anafvalidator.KitSupport#kitAvailable")
    @SuppressWarnings("unchecked")
    void invalidFixturesShowTheAnafMessages(String type, String fixture, String expectedCode) {
        ResponseEntity<Map<String, Object>> response = validate(type, fixture, "VALIDATE_AND_PDF");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("valid", false).containsEntry("pdfBase64", null);
        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");
        assertThat(errors).extracting(e -> e.get("code")).contains(expectedCode);
        assertThat(errors).allSatisfy(e -> assertThat((String) e.get("message")).isNotBlank());
    }

    @Test
    @EnabledIf("ro.ridelance.anafvalidator.KitSupport#kitAvailable")
    @SuppressWarnings("unchecked")
    void warningsDoNotBlockThePdf() {
        Map<String, Object> body = validate("D301", "d301-warning.xml", "VALIDATE_AND_PDF").getBody();

        assertThat(body).containsEntry("valid", true);
        assertThat((List<Map<String, Object>>) body.get("warnings"))
                .singleElement().satisfies(w -> assertThat(w).containsEntry("field", "email"));
        assertThat(body.get("pdfBase64")).isNotNull();
    }

    @Test
    @EnabledIf("ro.ridelance.anafvalidator.KitSupport#kitAvailable")
    void wrongDeclarationTypeIsAValidationResult() {
        Map<String, Object> body = validate("D301", "d100-valid.xml", "VALIDATE").getBody();

        assertThat(body).containsEntry("valid", false);
    }

    @Test
    void uploadsOverTheLimitAre413() {
        MultiValueMap<String, Object> form = form("D301", "big.xml", new byte[5 * 1024 * 1024 + 1], "VALIDATE");
        ResponseEntity<String> response = http.exchange("/v1/validate", HttpMethod.POST, entity(form), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    private ResponseEntity<Map<String, Object>> validate(String type, String fixture, String mode) {
        MultiValueMap<String, Object> form = form(type, fixture, KitSupport.fixture(fixture), mode);
        ResponseEntity<Map<String, Object>> response = http.exchange("/v1/validate", HttpMethod.POST, entity(form),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private MultiValueMap<String, Object> form(String type, String fileName, byte[] content, String mode) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("xml", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        form.add("declarationType", type);
        form.add("validatorVersion", KitSupport.kitDirectory().map(d -> d.getFileName().toString()).orElse("2026-09"));
        form.add("mode", mode);
        return form;
    }

    private static HttpEntity<MultiValueMap<String, Object>> entity(MultiValueMap<String, Object> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(InternalTokenFilter.HEADER, "test-token");
        return new HttpEntity<>(form, headers);
    }
}
