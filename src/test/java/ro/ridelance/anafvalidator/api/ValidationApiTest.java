package ro.ridelance.anafvalidator.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import ro.ridelance.anafvalidator.KitSupport;
import ro.ridelance.anafvalidator.core.DukIntegratorRunner;
import ro.ridelance.anafvalidator.core.DukRunResult;
import ro.ridelance.anafvalidator.core.RunnerTimeoutException;
import ro.ridelance.anafvalidator.core.Workspace;
import ro.ridelance.anafvalidator.security.InternalTokenFilter;

/** Contractul HTTP, cu un kit fals pe disc și DUKIntegrator înlocuit de un mock. */
@SpringBootTest(properties = "security.internalToken=test-token")
@AutoConfigureMockMvc
class ValidationApiTest {

    private static final String TOKEN = "test-token";

    @TempDir
    static Path temp;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DukIntegratorRunner runner;

    @BeforeAll
    static void fakeKit() throws IOException {
        Path lib = Files.createDirectories(temp.resolve("validators/2026-09/lib"));
        Files.writeString(temp.resolve("validators/2026-09/DUKIntegrator.jar"), "");
        for (String jar : new String[] {"D100Validator", "D100Pdf", "D301Validator", "D301Pdf", "D390Validator"}) {
            Files.writeString(lib.resolve(jar + ".jar"), "");
        }
        Files.createDirectories(temp.resolve("validators/jre6"));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("validators.path", () -> temp.resolve("validators").toString());
        registry.add("workspace.path", () -> temp.resolve("ws").toString());
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mvc.perform(get("/v1/validators"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        mvc.perform(get("/v1/validators").header(InternalTokenFilter.HEADER, "gresit"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsOpenAndUpWhenAKitIsInstalled() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void listsInstalledKits() throws Exception {
        mvc.perform(get("/v1/validators").header(InternalTokenFilter.HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].version").value("2026-09"))
                .andExpect(jsonPath("$[0].declarations[0]").value("D100"))
                .andExpect(jsonPath("$[0].declarations[2]").value("D390"))
                .andExpect(jsonPath("$[0].installedAt").isNotEmpty());
        mvc.perform(post("/v1/validators/reload").header(InternalTokenFilter.HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void invalidXmlIsA200WithParsedErrors() throws Exception {
        when(runner.run(any(), any(), any(), any()))
                .thenReturn(new DukRunResult(0, KitSupport.sample("d301-invalid-rules.txt"), "", null, 1500));

        mvc.perform(validate("d301-invalid.xml", "D301", "2026-09", "VALIDATE_AND_PDF").param("correlationId", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.declarationType").value("D301"))
                .andExpect(jsonPath("$.validatorVersion").value("2026-09"))
                .andExpect(jsonPath("$.errors", hasSize(6)))
                .andExpect(jsonPath("$.errors[5].code").value("R28"))
                .andExpect(jsonPath("$.errors[5].field").value("totalPlata_A"))
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andExpect(jsonPath("$.rawOutput").value(KitSupport.sample("d301-invalid-rules.txt")))
                .andExpect(jsonPath("$.pdfBase64").value(nullValue()))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void validXmlWithPdfReturnsTheFileAsBase64() throws Exception {
        byte[] pdf = "%PDF-1.4 test".getBytes();
        when(runner.run(any(), any(), any(), any())).thenAnswer(invocation -> {
            Workspace ws = invocation.getArgument(3);
            Files.write(ws.pdf(), pdf);
            return new DukRunResult(0, "ok", "", ws.pdf(), 1800);
        });

        mvc.perform(validate("d301-valid.xml", "D301", "2026-09", "VALIDATE_AND_PDF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.pdfBase64").value(Base64.getEncoder().encodeToString(pdf)));
    }

    @Test
    void validWithoutPdfIsReportedAsError() throws Exception {
        when(runner.run(any(), any(), any(), any())).thenReturn(new DukRunResult(0, "ok", "", null, 1800));

        mvc.perform(validate("d301-valid.xml", "D301", "2026-09", "VALIDATE_AND_PDF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("PDF_LIPSA"));
    }

    @Test
    void validateModeNeverReturnsPdf() throws Exception {
        when(runner.run(any(), any(), any(), any())).thenReturn(new DukRunResult(0, "ok", "", null, 900));

        mvc.perform(validate("d301-valid.xml", "D301", "2026-09", "VALIDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.pdfBase64").value(nullValue()));
    }

    @Test
    void badParametersAre400() throws Exception {
        mvc.perform(validate("d301-valid.xml", "D112", "2026-09", "VALIDATE")).andExpect(status().isBadRequest());
        mvc.perform(validate("d301-valid.xml", "D301", "2026-09", "SIGN")).andExpect(status().isBadRequest());
        mvc.perform(validate("d301-valid.xml", "D301", "../etc", "VALIDATE")).andExpect(status().isBadRequest());
        mvc.perform(validate("d301-valid.xml", "D301", "2026-09", "VALIDATE").param("correlationId", "a b"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/v1/validate").header(InternalTokenFilter.HEADER, TOKEN)
                        .param("declarationType", "D301").param("validatorVersion", "2026-09").param("mode", "VALIDATE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verify(runner, never()).run(any(), any(), any(), any());
    }

    @Test
    void unknownVersionOrMissingValidatorIs404() throws Exception {
        mvc.perform(validate("d301-valid.xml", "D301", "2020-01", "VALIDATE")).andExpect(status().isNotFound());
        mvc.perform(validate("d390-valid.xml", "D390", "2026-09", "VALIDATE_AND_PDF")).andExpect(status().isNotFound());
        mvc.perform(validate("d390-valid.xml", "D390", "jre6", "VALIDATE")).andExpect(status().isNotFound());
    }

    @Test
    void malformedXmlIs422WithoutCallingDukIntegrator() throws Exception {
        mvc.perform(validate("malformed.xml", "D301", "2026-09", "VALIDATE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").isNotEmpty());
        verify(runner, never()).run(any(), any(), any(), any());
    }

    @Test
    void timeoutIs504() throws Exception {
        when(runner.run(any(), any(), any(), any())).thenThrow(new RunnerTimeoutException("prea lent"));

        mvc.perform(validate("d301-valid.xml", "D301", "2026-09", "VALIDATE"))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void missingResultFileIs500WithCorrelationId() throws Exception {
        when(runner.run(any(), any(), any(), any()))
                .thenReturn(new DukRunResult(0, null, "Tip declaratie necunoscut", null, 300));

        mvc.perform(validate("d301-valid.xml", "D301", "2026-09", "VALIDATE").param("correlationId", "req-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.correlationId").value("req-500"));
    }

    private static MockMultipartHttpServletRequestBuilder validate(String fixture, String type, String version,
            String mode) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/v1/validate")
                .file(new MockMultipartFile("xml", fixture, "application/xml", KitSupport.fixture(fixture)))
                .header(InternalTokenFilter.HEADER, TOKEN)
                .param("declarationType", type)
                .param("validatorVersion", version)
                .param("mode", mode);
    }
}
