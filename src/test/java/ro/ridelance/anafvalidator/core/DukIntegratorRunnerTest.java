package ro.ridelance.anafvalidator.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import ro.ridelance.anafvalidator.KitSupport;
import ro.ridelance.anafvalidator.config.RunnerProperties;
import ro.ridelance.anafvalidator.config.ValidatorsProperties;
import ro.ridelance.anafvalidator.config.WorkspaceProperties;

class DukIntegratorRunnerTest {

    private static final List<String> JVM_ARGS = List.of("-Djava.awt.headless=true", "-Xmx256m");

    @TempDir
    Path temp;

    private WorkspaceManager workspaces;

    @BeforeEach
    void setUp() throws Exception {
        workspaces = new WorkspaceManager(new WorkspaceProperties(temp.resolve("ws"), 60));
        workspaces.init();
    }

    @Test
    void validateCommandFollowsTheNotes() {
        DukIntegratorRunner runner = new DukIntegratorRunner(new RunnerProperties(60, 1, "java", JVM_ARGS, 64));
        ValidatorKit kit = fakeKit(Path.of("/kit"));
        try (Workspace ws = workspaces.create()) {
            assertThat(runner.command(kit, DeclarationType.D301, ValidationMode.VALIDATE, ws)).containsExactly(
                    "java", "-Djava.awt.headless=true", "-Xmx256m", "-jar", kit.integratorJar().toString(),
                    "-v", "D301", ws.xml().toString(), "+" + ws.result());
        }
    }

    @Test
    void pdfCommandPassesZeroForOptionAndZip() {
        DukIntegratorRunner runner = new DukIntegratorRunner(new RunnerProperties(60, 1, "java", JVM_ARGS, 64));
        ValidatorKit kit = fakeKit(Path.of("/kit"));
        try (Workspace ws = workspaces.create()) {
            List<String> command = runner.command(kit, DeclarationType.D100, ValidationMode.VALIDATE_AND_PDF, ws);
            assertThat(command.subList(5, command.size())).containsExactly(
                    "-p", "D100", ws.xml().toString(), "+" + ws.result(), "0", "0", ws.pdf().toString());
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void killsTheProcessOnTimeout() throws Exception {
        Path fakeJava = temp.resolve("fake-java.sh");
        Files.writeString(fakeJava, "#!/bin/sh\nsleep 30\n");
        Files.setPosixFilePermissions(fakeJava, PosixFilePermissions.fromString("rwx------"));
        DukIntegratorRunner runner = new DukIntegratorRunner(
                new RunnerProperties(1, 1, fakeJava.toString(), List.of(), 64));

        long start = System.nanoTime();
        try (Workspace ws = workspaces.create()) {
            assertThatThrownBy(() -> runner.run(fakeKit(temp), DeclarationType.D301, ValidationMode.VALIDATE, ws))
                    .isInstanceOf(RunnerTimeoutException.class);
        }
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(10_000);
    }

    @Test
    @EnabledIf("ro.ridelance.anafvalidator.KitSupport#kitAvailable")
    void validatesValidAndInvalidXmlWithTheRealKit() throws Exception {
        ValidatorRegistry registry = new ValidatorRegistry(
                new ValidatorsProperties(KitSupport.validatorsRoot()));
        ValidatorKit kit = registry.reload().getFirst();
        DukIntegratorRunner runner = new DukIntegratorRunner(new RunnerProperties(60, 2, "", JVM_ARGS, 256));

        try (Workspace ws = workspaces.create()) {
            Files.write(ws.xml(), KitSupport.fixture("d301-valid.xml"));
            DukRunResult result = runner.run(kit, DeclarationType.D301, ValidationMode.VALIDATE_AND_PDF, ws);
            assertThat(result.resultOutput()).isEqualTo("ok");
            assertThat(result.pdf()).isNotNull();
            assertThat(Files.readAllBytes(result.pdf())).startsWith("%PDF".getBytes());
        }

        try (Workspace ws = workspaces.create()) {
            Files.write(ws.xml(), KitSupport.fixture("d301-invalid.xml"));
            DukRunResult result = runner.run(kit, DeclarationType.D301, ValidationMode.VALIDATE_AND_PDF, ws);
            assertThat(result.resultOutput()).contains("E: validari globale", "R28:");
            assertThat(result.pdf()).isNull();
        }
    }

    @Test
    void workspaceIsDeletedOnClose() {
        Path dir;
        try (Workspace ws = workspaces.create()) {
            dir = ws.dir();
            assertThat(dir).isDirectory();
        }
        assertThat(dir).doesNotExist();
    }

    @Test
    void startupCleanupRemovesOnlyStaleWorkspaces() throws Exception {
        Workspace stale = workspaces.create();
        Files.writeString(stale.xml(), "<x/>");
        Files.setLastModifiedTime(stale.dir(), FileTime.from(Instant.now().minusSeconds(7200)));
        Workspace fresh = workspaces.create();

        assertThat(workspaces.cleanupStale(Instant.now().minusSeconds(3600))).isEqualTo(1);
        assertThat(stale.dir()).doesNotExist();
        assertThat(fresh.dir()).isDirectory();
        fresh.close();
    }

    private static ValidatorKit fakeKit(Path dir) {
        return new ValidatorKit("test", dir, dir.resolve("DUKIntegrator.jar"),
                Set.of(DeclarationType.values()), Set.of(DeclarationType.values()), Instant.EPOCH);
    }
}
