# Release Notes

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
