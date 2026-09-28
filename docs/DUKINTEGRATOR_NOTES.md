# DUKIntegrator – note de explorare (J1)

Rulat pe 2026-09-26 cu kitul din `validators/2026-09/`, în containerul `eclipse-temurin:21-jre`
(kitul montat read-only în `/kit`, fișierele de test în `/work`).

Versiuni din kit:

| Componentă | Versiune |
|---|---|
| DUKIntegrator.jar | actualizat pe 2026-09-28 din `update5/zz9` (cu `lib/DecValidation.jar`, `Validator.jar`, `DecPdf.jar` din `update5/ss8`); build-ul din 2018 nu are `dec/DECTagCtx`, cerut de D700Validator |
| D100Validator.jar | J21.0.8 (14-Sep-2026) |
| D301Validator.jar | J1.2.5 (22-Oct-2020) |
| D390Validator.jar | J4.1.2 (25-Jun-2025) |
| D700Validator.jar | J5.0.3 (23-Apr-2026), `D700Pdf.jar` P4.0.3 |

Sursa interfeței: `validators/2026-09/doc/Instructiuni.txt` (secțiunea „B. modul linie de comanda”).

## Java

Documentația cere `java -version:1.6`. Opțiunea `-version:` nu mai există din Java 9, dar kitul rulează
**fără modificări pe Java 21** dacă e pornit direct cu `java -jar`. `DUKIntegrator.jar` își încarcă
dependențele prin `Class-Path` din manifest (`lib/DecPdf.jar lib/Validator.jar lib/DecValidation.jar …`),
iar validatoarele per declarație (`lib/DxxxValidator.jar`, `lib/DxxxPdf.jar`) sunt găsite relativ la jar.

Folosim `-Djava.awt.headless=true` (fără GUI) și **calea absolută** a jar-ului. Directorul curent nu contează
(am rulat cu `-w /tmp` și cu `-w /kit`, rezultat identic). Kitul poate fi montat read-only: DUKIntegrator nu
scrie nimic în folderul lui în modul linie de comandă.

`jre6/` (Java 6, 32 biți, Windows) și `config/*.cfg` (smartcard) nu sunt folosite.

## Comenzile folosite

Parametrii sunt poziționali. `$` = valoarea implicită pentru un parametru opțional urmat de alții.

### Validare (`VALIDATE`)

```
java -Djava.awt.headless=true -jar /kit/DUKIntegrator.jar -v D301 /work/decl.xml +/work/rezultat.txt
```

### Validare + PDF (`VALIDATE_AND_PDF`)

```
java -Djava.awt.headless=true -jar /kit/DUKIntegrator.jar -p D301 /work/decl.xml +/work/rezultat.txt 0 0 /work/decl.pdf
```

- al 4-lea argument: `fisierRezultat` (fișierul de erori). Implicit ar fi `<fisierXML>.err.txt`.
- prefixul `+` pe `fisierRezultat`: la `-p`, dacă validarea are **doar atenționări**, acestea rămân în fișier
  și `ok` se adaugă pe ultima linie. Fără prefix, atenționările se pierd și fișierul conține doar `ok`.
- al 5-lea: `optiuneValidare` = `0` (singura valoare relevantă pentru D100/D301/D390).
- al 6-lea: `fisierZIP` = `0`. Documentația: „la declaratiile fara ZIP atasat acest parametru trebuie sa fie 0”.
- al 7-lea: `fisierPDF`. Implicit ar fi `<fisierXML fără .xml>.pdf`.

Semnarea (`-s`, cu smartcard) nu e folosită: serviciul nu semnează.

## Unde apar rezultatele

| Ce | Unde |
|---|---|
| rezultatul validării | `fisierRezultat` (`+/work/rezultat.txt` → `/work/rezultat.txt`) |
| PDF-ul | `fisierPDF`; creat **numai** dacă validarea nu are erori |
| mesaje de progres | stdout (nu se parsează, doar se loghează) |
| erori interne (ex. validator lipsă) | `<fisierXML>.log`, lângă XML |

**Codul de ieșire al procesului e mereu `0`**: și pentru XML valid, și pentru XML invalid, și pentru tip de
declarație necunoscut, și pentru fișier XML inexistent. Nu poate fi folosit pentru a decide rezultatul.
Decizia se ia din `fisierRezultat` (și din existența lui).

## Formatul fișierului de rezultat

- valid, fără atenționări: exact textul `ok` (fără newline la final).
- altfel: o succesiune de blocuri. Fiecare bloc începe cu un antet nealiniat și continuă cu linii
  indentate cu un spațiu:

```
<L>: <locație> [<context opțional>]
 <descriere opțională, 0..n linii>
 <tip>[ <categorie>]: <detalii>
```

- `<L>` (din `DECValidatorRoot`): `E` = eroare, `A` = atenționare, `F` = eroare fatală (structură/parsare).
  În clase mai apare și `T`, pe care nu l-am întâlnit la rulare.
- `<locație>`: `validari globale`, `sectiune (1)`, `obligatie (1)`, `rezumat (1)`, `operatie (1)` …
- `<tip>`: `eroare` sau `atentionare`; `<categorie>`: `atribut`, `regula`, `structura` sau lipsă.
- `eroare atribut: <atribut>: <mesaj>`
- `eroare regula: <cod regulă>: <mesaj>` (cod ex. `R16`, `R5b`, `R15.1`, `R24.1`; o dată apare `.9`).
- la `-p` cu atenționări și prefixul `+`: blocurile `A:` urmate de o linie `ok` nealiniată.

Validitate: nu există blocuri `E`/`F` **și** (fișierul se termină cu `ok` **sau** conține doar blocuri `A`).
La `-v` cu doar atenționări, fișierul **nu** conține `ok`, deși declarația e validă.

## Exemple reale

Toate fișierele de mai jos sunt și în `src/test/resources/samples/`, iar XML-urile în
`src/test/resources/fixtures/` (date fictive: CIF `12345674`, cu cifra de control corectă).

### XML valid (`fixtures/d301-valid.xml`, `-v` și `-p`)

stdout:
```
1.
Validare fara erori fisier: /work/d301_valid.xml
Fisierul PDF a fost creat cu succes:
       /work/out.pdf
```
fișier rezultat (`samples/ok.txt`): `ok`. PDF-ul are ~30 KB și începe cu `%PDF-1.4`.
Durata unui `-p` pe D301: ~2 s (pornirea JVM inclusă).

Același rezultat `ok` pentru `fixtures/d100-valid.xml` și `fixtures/d390-valid.xml`.

### Erori de regulă (`fixtures/d301-invalid.xml`, `samples/d301-invalid-rules.txt`)

stdout:
```
1.
Erori la validare fisier: /work/d301_try.xml
       Erorile au fost scrise in fisierul: /work/d301_try.err.txt
```
fișier rezultat:
```
E: validari globale
 eroare regula: R5b: daca declaratia este initiala (d_rec = 0) atunci atributul temei trebuie sa fie diferit de 1
E: validari globale
 Nr. de evidenta a platii este eronat
 eroare regula: R16: nr_evid (10301082600125082026000) - pozitii fixe eronate
…
E: validari globale
 suma de control eronata
 eroare regula: R28: totalPlata_A (1420) = totalPlata_A calculat conform regulii (1210)
```
La `-p` pe același fișier: rezultat identic, **PDF-ul nu se creează**.

### Erori de atribut (`samples/d301-invalid-attributes.txt`)

```
E: validari globale
 eroare atribut: cif: CUI invalid ('12345675')
E: validari globale
 eroare atribut: functia_declarant: atributul trebuie sa existe
E: validari globale
 va rugam sa verificati daca folositi versiunea corecta de PDF inteligent sau daca XML-ul creat contine namespace-ul conform schemei XSD (pentru perioada de raportare)
 eroare atribut: foo: atribut necunoscut ('foo') in namespace='mfp:anaf:dgti:d301:declaratie:v1'
E: sectiune (1)
 eroare atribut: data_doc: data calendaristica eronata: '2026-08-10'
```

### Context în antet (`fixtures/d100-invalid.xml`, `samples/d100-invalid.txt`)

```
E: obligatie (1) [cod_oblig=604 scadenta=30.09.2026]
 eroare regula: R15.1: scadenta (30.09.2026) ar fi trebuit sa fie 25.09.2026 pt. cod obligatie=604
E: obligatie (1) [cod_oblig=604 scadenta=30.09.2026]
 eroare regula: .9: scadenta (30.09.2026) ar fi trebuit sa fie 25.09.2026 pt. cod obligatie 604
…
```

### D390 (`fixtures/d390-invalid.xml`, `samples/d390-invalid.txt`)

```
E: operatie (1)
 codO invalid (nu respecta algoritmul de tara)
 eroare regula: R24.1: operatorul codO = '123' trebuie sa respecte algoritmul specific 'EE'
```

### Atenționare (`fixtures/d301-warning.xml`)

`-v` (`samples/d301-warning-validate.txt`), stdout `Atentionari la validare fisier: …`:
```
A: validari globale
 atentionare atribut: email: Email invalid ('x@')
```
`-p` cu `+` (`samples/d301-warning-pdf.txt`): același bloc + linia `ok`; PDF-ul se creează.

### XML malformat (`fixtures/malformed.xml`, `samples/fatal-parse.txt`)

```
F: validari globale
 eroare: Eroare fatala de parsare: 'org.xml.sax.SAXParseException; systemId: file:///work/broken.xml; lineNumber: 1; columnNumber: 1; Content is not allowed in prolog.'
```
Serviciul nu ajunge aici: XML-ul malformat e respins cu `422` înainte de DUKIntegrator.

### Tip greșit (D100 validat ca D301, `samples/wrong-type.txt`)

```
F: validari globale
 eroare structura: sectiune necunoscuta ('declaratie100')
```

### Cazuri fără fișier de rezultat

| Situație | stdout | fișier rezultat | cod ieșire |
|---|---|---|---|
| tip necunoscut (`-v D999`) | `Tip declaratie necunoscut: D999` | nu se creează | 0 |
| XML inexistent | `fisier XML incorect specificat` | nu se creează | 0 |

La tip necunoscut apare și `<fisierXML>.log`: `?   modul Validator; eroare=-8: nu gasesc /kit/lib/D999Validator.jar`.
Serviciul evită ambele cazuri prin whitelist (tipul trebuie să existe în kit) și scrierea XML-ului înainte de rulare;
dacă totuși lipsește fișierul de rezultat, răspunde `500`.
