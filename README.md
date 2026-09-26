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

## API

Toate cererile cer header-ul `X-Internal-Token`; fără el, `401`. Excepție: `GET /actuator/health`
(folosit de Coolify, întoarce doar `UP`/`DOWN`).

### `POST /v1/validate`

`multipart/form-data`:

| Câmp | Valori |
|---|---|
| `xml` | fișier, max 5 MB |
| `declarationType` | `D100` \| `D301` \| `D390` |
| `validatorVersion` | numele folderului din `validators/`, ex. `2026-09` |
| `mode` | `VALIDATE` \| `VALIDATE_AND_PDF` |
| `correlationId` | opțional, `[A-Za-z0-9._:-]{1,100}`; altfel se ia din header-ul `X-Correlation-Id` sau se generează |

```bash
curl -H "X-Internal-Token: dev-token-schimba-ma" -F xml=@src/test/resources/fixtures/d301-valid.xml -F declarationType=D301 -F validatorVersion=2026-09 -F mode=VALIDATE_AND_PDF http://localhost:8090/v1/validate
```

Răspuns `200`, și când XML-ul e invalid (invaliditatea e un rezultat, nu o eroare):

```json
{
  "valid": false,
  "declarationType": "D100",
  "validatorVersion": "2026-09",
  "errors": [{
    "code": "R16",
    "message": "Nr. de evidenta a platii este eronat: nr_evid (…) - scadenta platii eronata",
    "field": "nr_evid",
    "location": "obligatie (1) [cod_oblig=604 scadenta=30.09.2026]"
  }],
  "warnings": [],
  "rawOutput": "E: obligatie (1) …",
  "pdfBase64": null,
  "durationMs": 1354,
  "correlationId": "…"
}
```

- `code`: codul regulii ANAF (`R16`, `R5b`, `R15.1` …) sau categoria: `ATRIBUT`, `STRUCTURA`, `FATAL`,
  `EROARE`, `NERECUNOSCUT` (format necunoscut; tot textul e în `message`), `PDF_LIPSA`.
- `field`: best-effort, extras din mesaj. `location`: antetul blocului ANAF.
- `pdfBase64`: doar la `VALIDATE_AND_PDF` și `valid = true`. Atenționările (`warnings`) nu blochează PDF-ul.
- `rawOutput`: mereu conținutul brut al fișierului de rezultat DUKIntegrator.

| Cod | Situație |
|---|---|
| `400` | parametri lipsă sau invalizi |
| `404` | `validatorVersion` necunoscut, sau kitul nu are validator/PDF pentru declarație |
| `413` | fișier peste 5 MB |
| `422` | XML-ul nu e bine format (DTD/entități externe interzise); DUKIntegrator nu se mai apelează |
| `504` | DUKIntegrator nu a terminat în `RUNNER_TIMEOUT_SECONDS` (sau nu s-a eliberat un loc în același interval) |
| `500` | eroare internă; corpul conține `correlationId` |

### `GET /v1/validators`

```json
[{ "version": "2026-09", "declarations": ["D100","D301","D390"], "installedAt": "…" }]
```

### `POST /v1/validators/reload`

Rescanează `validators/` și întoarce aceeași listă. Folosit după instalarea unui kit nou.

### `GET /actuator/health`, `GET /actuator/metrics/anaf.validation`

Health e `DOWN` dacă nu există nicio versiune de kit instalată. Metrica `anaf.validation` (timer) are tag-urile
`declarationType`, `mode` și `outcome` (`valid`, `invalid`, `malformed`, `timeout`, `error`); de exemplu
`/actuator/metrics/anaf.validation?tag=outcome:timeout` dă numărul de timeout-uri.

## Operare

- Logurile sunt JSON (ECS) pe stdout. Fiecare validare lasă o linie cu `correlationId`, `declarationType`,
  `validatorVersion`, `mode`, `outcome`, `valid`, `durationMs`. **Conținutul XML nu se loghează.**
- Fiecare cerere lucrează în `WORKSPACE_PATH/{uuid}/`, șters la final. La pornire se șterg resturile mai vechi de o oră.
- Mai multe versiuni de kit pot sta instalate simultan (`validators/2026-01`, `validators/2026-09`); backend-ul
  .NET alege versiunea după `AnafDeclarationSchema.ValidatorVersion` pentru perioada declarației.

## Deploy (Coolify)

- Imaginea se construiește din `Dockerfile` (build Maven în container, rulare pe `eclipse-temurin:21-jre`, user `app`).
- `validators/` se montează ca **volum persistent** în `/validators` (poate fi read-only).
- Serviciul rulează **doar în rețeaua internă** Coolify, fără domeniu public.
- Variabile: `INTERNAL_TOKEN`, `VALIDATORS_PATH`, `RUNNER_TIMEOUT_SECONDS`, `RUNNER_MAX_CONCURRENT`.
- În backend-ul .NET: `AnafValidator:BaseUrl` și `AnafValidator:Token`.

### Actualizare kit (când ANAF publică un validator nou)

1. Descarcă kitul (DUKIntegrator și/sau arhivele Soft J ale declarațiilor).
2. Copiază-l complet în `validators/{versiune-nouă}/` (ex. pornind de la o copie a versiunii curente, cu noile
   `DxxxValidator.jar` / `DxxxPdf.jar` în `lib/`).
3. `POST /v1/validators/reload` (cu token) și verifică în răspuns că versiunea nouă apare.
4. Adaugă în RIDElance `AnafDeclarationSchema` cu noua versiune și perioada de valabilitate.

Fără redeploy. Versiunile vechi rămân disponibile pentru declarațiile perioadelor anterioare.

## Configurare

| Variabilă | Implicit | Rol |
|---|---|---|
| `INTERNAL_TOKEN` | — (obligatoriu) | valoarea așteptată în `X-Internal-Token` |
| `VALIDATORS_PATH` | `./validators` (`/validators` în imagine) | folderul cu kiturile |
| `RUNNER_TIMEOUT_SECONDS` | `60` | timeout per proces DUKIntegrator |
| `RUNNER_MAX_CONCURRENT` | `2` | procese DUKIntegrator simultane |
| `WORKSPACE_PATH` | `${java.io.tmpdir}/anaf-validator` | directoarele temporare per cerere |
| `RUNNER_JAVA_COMMAND` | java-ul serviciului | executabilul java pentru procesul DUKIntegrator |
| `LOG_FORMAT` | `ecs` | formatul logurilor structurate (`ecs`, `logstash`, `gelf`) |

## Ce NU face serviciul

- Nu semnează și nu depune declarații.
- Nu calculează sume și nu modifică XML-ul primit.
- Nu păstrează nimic după răspuns.
- Nu e accesibil din internet.
