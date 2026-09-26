package ro.ridelance.anafvalidator.core;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import ro.ridelance.anafvalidator.KitSupport;

class XmlWellFormednessCheckerTest {

    private final XmlWellFormednessChecker checker = new XmlWellFormednessChecker();

    @Test
    void acceptsDeclarations() {
        assertThatCode(() -> checker.check(KitSupport.fixture("d301-valid.xml"))).doesNotThrowAnyException();
        assertThatCode(() -> checker.check(KitSupport.fixture("d390-invalid.xml"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> checker.check(KitSupport.fixture("malformed.xml")))
                .isInstanceOf(MalformedXmlException.class)
                .hasMessageContaining("linia 1");
    }

    @Test
    void rejectsExternalEntities() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE d [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <declaratie301>&x;</declaratie301>
                """;
        assertThatThrownBy(() -> checker.check(xxe.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(MalformedXmlException.class)
                .hasMessageContaining("DOCTYPE");
    }

    @Test
    void rejectsEntityExpansion() {
        String bomb = """
                <?xml version="1.0"?>
                <!DOCTYPE d [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">]>
                <d>&b;</d>
                """;
        assertThatThrownBy(() -> checker.check(bomb.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(MalformedXmlException.class);
    }
}
