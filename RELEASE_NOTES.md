# Release Notes

## 2.0.0 — 2026-07-11

Major release: migrazione dello stack applicativo a **Spring Boot 4 / Spring Framework 7** (con Spring Batch 6, Hibernate 7, Jackson 3, Java 21).

### Aggiornamenti dipendenze
- `govpay-bom` aggiornato a **2.0.1** (parent BOM): Spring Boot **4.1.0**, Spring Framework **7.0.8**, Spring Batch **6.0.4**, Hibernate **7.4.1**, Jackson **3.1.4**, Java **21**.
- `govpay-common` aggiornato a **2.0.0**.
- `openapi-generator-maven-plugin` aggiornato a **7.23.0**; il client Maggioli è ora generato per Spring Boot 4 / Jackson 3 (`serializationLibrary=jackson`, `useSpringBoot4=true`, `useJackson3=true`).

### Migrazione Jackson 2 → 3 (`tools.jackson`)
- `WebConfig`: `ObjectMapper` ricostruito con l'API builder immutabile di Jackson 3 (`JsonMapper.builder()`, `EnumFeature`/`DateTimeFeature`), coerente con i serializer `OffsetDateTime` di `govpay-common` 2.0.0.
- `GdeService`: `ObjectMapper` migrato a `tools.jackson.databind.ObjectMapper` (richiesto da `AbstractGdeService`).

### Migrazione Spring Batch 5 → 6
- Adeguati i package riorganizzati (`core.job`, `core.step`, `core.job.parameters`, `core.listener`, `infrastructure.item`, `infrastructure.repeat`) e la rinomina `JobParametersInvalidException` → `InvalidJobParametersException`.
- `JobLauncher` sostituito da `JobOperator` (`run()` → `start()`) in `BatchJobConfiguration` e `MaggioliJppaBatchScheduler`.
- `JobExplorer` sostituito da `JobRepository` in `BatchController`, allineato alle nuove firme di `AbstractBatchController` e `JobConcurrencyService`.

### Migrazione Spring Boot 4
- `@EntityScan` spostato nel package `org.springframework.boot.persistence.autoconfigure`.
- Nuova classe `BatchInfraConfig`: i bean infrastrutturali (`taskExecutor`, `jobExecutionHelper`, `jobConcurrencyService`) sono stati estratti da `BatchJobConfiguration` per evitare la dipendenza circolare `entityManagerFactory → batchJobConfiguration → transactionManager` introdotta dal nuovo bootstrap JPA di Spring Boot 4.

### Compatibilità
Release major con **breaking change** infrastrutturali: richiede Java 21 a runtime e l'ecosistema GovPay allineato al BOM 2.x (`govpay-common` 2.0.0). Nessuna modifica al comportamento funzionale del batch di notifica.

## 1.0.3 — 2026-05-12

Release di manutenzione: pulizia della configurazione di logging.

### Configurazione
- Rimosse da `application.properties` le direttive `logging.level.*` (root, `it.govpay.maggioli.batch`, `org.springframework.batch`, `org.springframework.web.client`). La configurazione di logging è ora demandata al runtime (env, profili dedicati, configurazione esterna). I livelli verbosi per i test restano in `application-test.properties`.

### Compatibilità
Nessuna breaking change. In caso si volessero ripristinare i livelli precedenti, è sufficiente fornirli via variabili d'ambiente, `--logging.level.*` o profilo dedicato.

## 1.0.2 — 2026-05-06

Release di manutenzione: aggiornamento dipendenze GovPay e potenziamento della pipeline di build/release.

### Aggiornamenti dipendenze
- `govpay-bom` aggiornato a **1.1.3** (parent BOM).
- `govpay-common` aggiornato a **1.1.2**.

### Pipeline
- **SBOM CycloneDX**: aggiunto job `sbom` che genera l'SBOM aggregato (formati `json` + `xml`, schema 1.6) tramite `cyclonedx-maven-plugin`. Eseguito su push su `main`/tag o su richiesta esplicita (`vars.FORCE_SBOM_JOB`); disattivabile con `vars.DISABLE_SBOM_JOB`. L'SBOM viene incluso nel ZIP `release-reports` sotto `reports/sbom/`.
- **OSV Scanner**: aggiunto job `osv-scan` (Google OSV Scanner) eseguito su `main`/tag con fallimento bloccante. Il report SARIF è incluso nel ZIP `release-reports` sotto `reports/osv/`.
- **Cache OWASP Dependency-Check**: chiave basata sulla data e flag `NOUPDATE_FLAG` per saltare l'aggiornamento NVD quando la cache è della stessa giornata.
- **Workflow `refresh-owasp-db`**: aggiornamento notturno della cache NVD per ridurre la latenza dei job di build.
- **Reports ZIP unico**: tutti i report (OWASP, JaCoCo, OSV, licenze, SBOM) collezionati in `release-reports-<tag>.zip` allegato alla GitHub Release.
- **Bump action GitHub**: `actions/checkout` v6, `actions/setup-java` v5, `actions/cache` v5, `actions/upload-artifact` e `actions/download-artifact` v7.
- **Fix step "Zip SQL files"**: aggiunto `mkdir -p target` prima dello zip per evitare errori nel checkout pulito del job release.

### Codice
- `GdeService`: implementato il nuovo metodo astratto `getConfigurazioneComponente(ComponenteEvento, Giornale)` introdotto da `govpay-common` 1.1.2, con mapping per i componenti standard incluso `API_MAGGIOLI_JPPA`.
- Aggiunti script SQL di svecchiamento delle tabelle Spring Batch (`spring-batch-cleanup.sql`) per tutti i database supportati (PostgreSQL, MySQL, Oracle, SQL Server, HSQLDB).

### Compatibilità
Nessuna breaking change. Aggiornamento drop-in rispetto alla 1.0.1.
