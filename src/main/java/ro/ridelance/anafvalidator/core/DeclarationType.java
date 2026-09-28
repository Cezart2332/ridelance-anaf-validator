package ro.ridelance.anafvalidator.core;

import java.util.Arrays;
import java.util.Optional;

/** Declarațiile acceptate. Valoarea ajunge ca argument DUKIntegrator, deci lista e whitelist-ul. */
public enum DeclarationType {
    D100,
    D301,
    D390,
    /** Cererea de cod de TVA art. 317 (declarație de mențiuni), nu o declarație lunară. */
    D700;

    public static Optional<DeclarationType> parse(String value) {
        return Arrays.stream(values()).filter(t -> t.name().equals(value)).findFirst();
    }

    /** Jar-ul de validare din {@code lib/} al kitului. */
    public String validatorJar() {
        return name() + "Validator.jar";
    }

    /** Jar-ul de generare PDF din {@code lib/} al kitului. */
    public String pdfJar() {
        return name() + "Pdf.jar";
    }
}
