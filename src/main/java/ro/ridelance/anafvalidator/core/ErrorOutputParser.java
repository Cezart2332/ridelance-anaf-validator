package ro.ridelance.anafvalidator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Transformă fișierul de rezultat DUKIntegrator în erori și atenționări. Formatul e descris, cu exemple
 * reale, în {@code docs/DUKINTEGRATOR_NOTES.md}:
 *
 * <pre>
 * E: obligatie (1) [cod_oblig=604 scadenta=30.09.2026]
 *  Nr. de evidenta a platii este eronat
 *  eroare regula: R16: nr_evid (…) - scadenta platii eronata
 * </pre>
 *
 * Ce nu se recunoaște ajunge integral într-o eroare {@code NERECUNOSCUT}; nu se pierde nimic.
 */
@Component
public class ErrorOutputParser {

    static final String OK = "ok";
    static final String UNRECOGNIZED = "NERECUNOSCUT";

    /** Antetul unui bloc: litera de severitate la început de linie. */
    private static final Pattern HEADER = Pattern.compile("^([AEFT]):\\s?(.*)$");
    /** Linia cu detaliul: {@code eroare regula: …}, {@code atentionare atribut: …}, {@code eroare: …}. */
    private static final Pattern DETAIL =
            Pattern.compile("^(eroare|atentionare)(?: (atribut|regula|structura))?:\\s*(.*)$");
    /** {@code <atribut>: <mesaj>} */
    private static final Pattern ATTRIBUTE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*):\\s*(.*)$");
    /** {@code <cod regulă>: <mesaj>}; codul are cel puțin o cifră: R16, R5b, R15.1, .9 */
    private static final Pattern RULE = Pattern.compile("^([^\\s:]*\\d[^\\s:]*):\\s*(.*)$");
    /** Atributul numit la începutul mesajului unei reguli: {@code nr_evid (…)}, {@code operatorul codO = …}. */
    private static final Pattern RULE_FIELD =
            Pattern.compile("^(?:operatorul\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\(|=)");

    public ParsedOutput parse(String raw) {
        List<ValidationMessage> errors = new ArrayList<>();
        List<ValidationMessage> warnings = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new ParsedOutput(false, errors, warnings);
        }

        boolean ok = false;
        List<String> unrecognized = new ArrayList<>();
        Block block = null;
        for (String line : raw.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                if (block != null) {
                    block.addTo(errors, warnings);
                }
                block = new Block(header.group(1).charAt(0), header.group(2).trim());
            } else if (line.equals(OK)) {
                ok = true;
            } else if (block != null && Character.isWhitespace(line.charAt(0))) {
                block.body.add(line.trim());
            } else {
                unrecognized.add(line);
            }
        }
        if (block != null) {
            block.addTo(errors, warnings);
        }
        boolean recognized = ok || !errors.isEmpty() || !warnings.isEmpty();
        if (!recognized) {
            errors.add(new ValidationMessage(UNRECOGNIZED, raw.strip(), null, null));
        } else if (!unrecognized.isEmpty()) {
            errors.add(new ValidationMessage(UNRECOGNIZED, String.join("\n", unrecognized), null, null));
        }
        return new ParsedOutput(ok, errors, warnings);
    }

    /**
     * @param okMarker DUKIntegrator a scris {@code ok} (singur sau după atenționări)
     */
    public record ParsedOutput(boolean okMarker, List<ValidationMessage> errors, List<ValidationMessage> warnings) {

        /** Fără erori, și fie {@code ok}, fie doar atenționări (la {@code -v} nu se scrie {@code ok} după ele). */
        public boolean valid() {
            return errors.isEmpty() && (okMarker || !warnings.isEmpty());
        }
    }

    private static final class Block {

        private final char severity;
        private final String location;
        private final List<String> body = new ArrayList<>();

        Block(char severity, String location) {
            this.severity = severity;
            this.location = location;
        }

        void addTo(List<ValidationMessage> errors, List<ValidationMessage> warnings) {
            List<String> description = new ArrayList<>();
            String kind = null;
            String category = null;
            String detail = null;
            for (String line : body) {
                Matcher m = DETAIL.matcher(line);
                if (detail == null && m.matches()) {
                    kind = m.group(1);
                    category = m.group(2);
                    detail = m.group(3);
                } else {
                    description.add(line);
                }
            }

            String code;
            String field = null;
            String text;
            if (detail == null) {
                code = severity == 'F' ? "FATAL" : severity == 'A' ? "ATENTIONARE" : "EROARE";
                text = String.join(" ", description);
            } else {
                code = severity == 'F' ? "FATAL" : (category != null ? category : kind).toUpperCase();
                text = detail;
                if ("atribut".equals(category)) {
                    Matcher a = ATTRIBUTE.matcher(detail);
                    if (a.matches()) {
                        field = a.group(1);
                        text = a.group(2);
                    }
                } else if ("regula".equals(category)) {
                    Matcher r = RULE.matcher(detail);
                    if (r.matches()) {
                        code = r.group(1);
                        text = r.group(2);
                        Matcher f = RULE_FIELD.matcher(text);
                        if (f.find()) {
                            field = f.group(1);
                        }
                    }
                }
                if (!description.isEmpty()) {
                    text = String.join(" ", description) + ": " + text;
                }
            }
            if (text.isBlank()) {
                text = location;
            }

            boolean warning = severity == 'A' || (severity == 'T' && "atentionare".equals(kind));
            (warning ? warnings : errors).add(new ValidationMessage(code, text, field, location));
        }
    }
}
