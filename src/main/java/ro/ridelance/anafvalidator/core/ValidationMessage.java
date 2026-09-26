package ro.ridelance.anafvalidator.core;

/**
 * O eroare sau o atenționare ANAF.
 *
 * @param code     codul regulii ({@code R16}, {@code R5b}) sau categoria ({@code ATRIBUT}, {@code STRUCTURA},
 *                 {@code FATAL}, {@code NERECUNOSCUT} …)
 * @param message  mesajul ANAF, cu liniile de descriere ale blocului în față
 * @param field    atributul vizat, best-effort; {@code null} când mesajul nu îl numește
 * @param location antetul blocului, ex. {@code obligatie (1) [cod_oblig=604 scadenta=30.09.2026]}
 */
public record ValidationMessage(String code, String message, String field, String location) {
}
