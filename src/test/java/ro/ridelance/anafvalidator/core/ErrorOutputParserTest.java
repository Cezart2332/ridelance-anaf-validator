package ro.ridelance.anafvalidator.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;

import ro.ridelance.anafvalidator.KitSupport;
import ro.ridelance.anafvalidator.core.ErrorOutputParser.ParsedOutput;

/** Pe mostrele reale din {@code docs/DUKINTEGRATOR_NOTES.md} ({@code src/test/resources/samples}). */
class ErrorOutputParserTest {

    private final ErrorOutputParser parser = new ErrorOutputParser();

    @Test
    void okIsValid() {
        ParsedOutput out = parser.parse(KitSupport.sample("ok.txt"));
        assertThat(out.valid()).isTrue();
        assertThat(out.errors()).isEmpty();
        assertThat(out.warnings()).isEmpty();
    }

    @Test
    void ruleErrorsKeepCodeFieldAndDescription() {
        ParsedOutput out = parser.parse(KitSupport.sample("d301-invalid-rules.txt"));

        assertThat(out.valid()).isFalse();
        assertThat(out.warnings()).isEmpty();
        assertThat(out.errors()).extracting(ValidationMessage::code, ValidationMessage::field)
                .containsExactly(
                        tuple("R5b", null),
                        tuple("R16", "nr_evid"),
                        tuple("R16", "nr_evid"),
                        tuple("R16", "nr_evid"),
                        tuple("R16", "nr_evid"),
                        tuple("R28", "totalPlata_A"));
        assertThat(out.errors().get(1).message())
                .isEqualTo("Nr. de evidenta a platii este eronat: nr_evid (10301082600125082026000) - pozitii fixe eronate");
        assertThat(out.errors().get(5).message())
                .isEqualTo("suma de control eronata: totalPlata_A (1420) = totalPlata_A calculat conform regulii (1210)");
        assertThat(out.errors()).allSatisfy(e -> assertThat(e.location()).isEqualTo("validari globale"));
    }

    @Test
    void attributeErrorsExposeTheAttribute() {
        ParsedOutput out = parser.parse(KitSupport.sample("d301-invalid-attributes.txt"));

        assertThat(out.errors()).extracting(ValidationMessage::code, ValidationMessage::field, ValidationMessage::location)
                .containsExactly(
                        tuple("ATRIBUT", "cif", "validari globale"),
                        tuple("ATRIBUT", "functia_declarant", "validari globale"),
                        tuple("ATRIBUT", "foo", "validari globale"),
                        tuple("ATRIBUT", "data_doc", "sectiune (1)"));
        assertThat(out.errors().getFirst().message()).isEqualTo("CUI invalid ('12345675')");
        assertThat(out.errors().get(2).message())
                .startsWith("va rugam sa verificati")
                .endsWith(": atribut necunoscut ('foo') in namespace='mfp:anaf:dgti:d301:declaratie:v1'");
    }

    @Test
    void headerContextBecomesLocation() {
        ParsedOutput out = parser.parse(KitSupport.sample("d100-invalid.txt"));

        assertThat(out.errors()).extracting(ValidationMessage::code, ValidationMessage::field)
                .containsExactly(tuple("R15.1", "scadenta"), tuple(".9", "scadenta"), tuple("R16", "nr_evid"));
        assertThat(out.errors().getFirst().location()).isEqualTo("obligatie (1) [cod_oblig=604 scadenta=30.09.2026]");
    }

    @Test
    void d390RuleNamesTheOperatorField() {
        ParsedOutput out = parser.parse(KitSupport.sample("d390-invalid.txt"));

        assertThat(out.errors()).singleElement().satisfies(e -> {
            assertThat(e.code()).isEqualTo("R24.1");
            assertThat(e.field()).isEqualTo("codO");
            assertThat(e.location()).isEqualTo("operatie (1)");
            assertThat(e.message()).startsWith("codO invalid (nu respecta algoritmul de tara): operatorul codO = '123'");
        });
    }

    @Test
    void warningsOnlyAreValidEvenWithoutOk() {
        ParsedOutput out = parser.parse(KitSupport.sample("d301-warning-validate.txt"));

        assertThat(out.okMarker()).isFalse();
        assertThat(out.valid()).isTrue();
        assertThat(out.errors()).isEmpty();
        assertThat(out.warnings()).singleElement().satisfies(w -> {
            assertThat(w.code()).isEqualTo("ATRIBUT");
            assertThat(w.field()).isEqualTo("email");
            assertThat(w.message()).isEqualTo("Email invalid ('x@')");
        });
    }

    @Test
    void warningsFollowedByOkInPdfMode() {
        ParsedOutput out = parser.parse(KitSupport.sample("d301-warning-pdf.txt"));

        assertThat(out.okMarker()).isTrue();
        assertThat(out.valid()).isTrue();
        assertThat(out.warnings()).hasSize(1);
        assertThat(out.errors()).isEmpty();
    }

    @Test
    void fatalBlocksAreErrors() {
        ParsedOutput parse = parser.parse(KitSupport.sample("fatal-parse.txt"));
        ParsedOutput wrongType = parser.parse(KitSupport.sample("wrong-type.txt"));

        assertThat(parse.valid()).isFalse();
        assertThat(parse.errors()).singleElement().satisfies(e -> {
            assertThat(e.code()).isEqualTo("FATAL");
            assertThat(e.message()).startsWith("Eroare fatala de parsare");
        });
        assertThat(wrongType.errors()).singleElement().satisfies(e -> {
            assertThat(e.code()).isEqualTo("FATAL");
            assertThat(e.message()).isEqualTo("sectiune necunoscuta ('declaratie100')");
        });
    }

    @Test
    void unknownFormatKeepsTheWholeText() {
        ParsedOutput out = parser.parse("Ceva complet nou\nPe doua linii\n");

        assertThat(out.valid()).isFalse();
        assertThat(out.errors()).singleElement().satisfies(e -> {
            assertThat(e.code()).isEqualTo(ErrorOutputParser.UNRECOGNIZED);
            assertThat(e.message()).isEqualTo("Ceva complet nou\nPe doua linii");
        });
    }

    @Test
    void strayLinesNextToBlocksAreNotLost() {
        ParsedOutput out = parser.parse("A: validari globale\n atentionare: ceva\nlinie noua\n");

        assertThat(out.valid()).isFalse();
        assertThat(out.warnings()).hasSize(1);
        assertThat(out.errors()).singleElement()
                .satisfies(e -> assertThat(e.message()).isEqualTo("linie noua"));
    }

    @Test
    void blankOutputIsNotValid() {
        assertThat(parser.parse("").valid()).isFalse();
    }
}
