# ridelance-anaf-validator

Serviciu intern care primește XML-ul unei declarații ANAF (D100 / D301 / D390), îl validează cu
**DUKIntegrator + validatorul oficial ANAF** și, la cerere, generează PDF-ul cu XML-ul atașat.

Îl apelează doar backend-ul .NET RIDElance. Nu se expune public.

Stack: Java 21, Spring Boot 3 (web + actuator), Maven. Fără bază de date.
DUKIntegrator rulează ca **proces JVM separat** pentru fiecare cerere (vezi `docs/DUKINTEGRATOR_NOTES.md`).

## Kitul ANAF

Kitul nu se comite în git. Se pune în `validators/{versiune}/` (pe server, într-un volum montat în `/validators`).

De unde se descarcă:

1. **DUKIntegrator**: pagina ANAF „Descărcare declarații” → secțiunea *Soft J* → „DUKIntegrator” (arhiva `dist`).
2. **Validatoarele**: aceeași pagină, câte o arhivă *Soft J* pentru D100, D301 și D390. Fiecare conține
   `DxxxValidator.jar` și `DxxxPdf.jar`.

Cum se așază (un folder per versiune de kit, ex. `2026-09`):

```
validators/
└── 2026-09/
    ├── DUKIntegrator.jar
    ├── config/
    ├── doc/                  # documentația kitului (Instructiuni.txt etc.)
    └── lib/
        ├── DecPdf.jar, DecValidation.jar, Validator.jar, bcmail/bcprov/iText …
        ├── D100Validator.jar, D100Pdf.jar
        ├── D301Validator.jar, D301Pdf.jar
        └── D390Validator.jar, D390Pdf.jar
```

Folderul `jre6/` din kit (Java 6 pe 32 de biți, pentru Windows) nu e necesar: serviciul folosește Java 21.

## Rulare locală

Nu e nevoie de Java instalat; totul merge prin Docker.

```bash
docker compose -f docker-compose.dev.yml up --build
```

Serviciul ascultă pe `http://localhost:8090`, cu tokenul `dev-token-schimba-ma` (sau `INTERNAL_TOKEN` din mediu).

```bash
curl http://localhost:8090/actuator/health
```

```bash
curl -H "X-Internal-Token: dev-token-schimba-ma" http://localhost:8090/v1/validators
```

### Build și teste

```bash
docker run --rm -v "$PWD:/app" -v anaf-validator-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -B verify
```

Pe Git Bash (Windows) prefixează comanda cu `MSYS_NO_PATHCONV=1`.
Testele care rulează DUKIntegrator pe bune pornesc doar dacă există un kit în `validators/`; altfel sunt sărite.

## Configurare

| Variabilă | Implicit | Rol |
|---|---|---|
| `INTERNAL_TOKEN` | — (obligatoriu) | valoarea așteptată în `X-Internal-Token` |
| `VALIDATORS_PATH` | `./validators` (`/validators` în imagine) | folderul cu kiturile |
| `RUNNER_TIMEOUT_SECONDS` | `60` | timeout per proces DUKIntegrator |
| `RUNNER_MAX_CONCURRENT` | `2` | procese DUKIntegrator simultane |
| `WORKSPACE_PATH` | `${java.io.tmpdir}/anaf-validator` | directoarele temporare per cerere |
