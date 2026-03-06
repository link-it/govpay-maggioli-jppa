<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Connettore Maggioli JPPA

[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-maggioli-jppa?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-maggioli-jppa)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-maggioli-jppa/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

## Sommario

Batch Spring Boot per la notifica automatica dei pagamenti al servizio Maggioli JPPA tramite API REST.
Il sistema legge le ricevute di pagamento da GovPay e le invia al servizio Maggioli, producendo un tracciato di esito in formato CSV/ZIP e inviandolo opzionalmente via email.

## Architettura Implementata

### Step 1: Cleanup tabella JPPA_NOTIFICHE
- **Funzione**: Svuota la tabella `JPPA_NOTIFICHE` prima di iniziare il processo

### Step 2: Acquisizione Headers (Multi-threaded)
- **Reader**: `MaggioliJppaHeadersReader` - Legge i domini abilitati dal database
- **Processor**: `MaggioliJppaHeadersProcessor` - Per ogni dominio, recupera le ricevute da notificare
- **Writer**: `MaggioliJppaHeadersWriter` - Salva i riferimenti in `JPPA_NOTIFICHE`
- **Parallelizzazione**: Configurabile tramite `govpay.batch.thread-pool-size`

### Step 3: Invio Notifiche (PARTIZIONATO per dominio)
- **Partitioner**: `DominioPartitioner` - Crea una partizione per ogni dominio
- **Reader**: `SendNotificationReader` - Legge i pagamenti da notificare per dominio
- **Processor**: `SendNotificationProcessor` - Per ogni pagamento:
  - Chiama il login API Maggioli per ottenere il token Bearer
  - Invia la notifica di pagamento via `POST /rest/notifiche/v2/pagamenti`
  - Gestisce retry e skip su errori HTTP 400
- **Writer**: `SendNotificationWriter` - Aggiorna `dataUltimaRt`, genera tracciato CSV/ZIP, invia email di esito
- **Parallelizzazione**: Ogni dominio viene processato in una partizione separata

## Configurazione Connettore

La configurazione verso il servizio Maggioli JPPA (URL, credenziali, SSL, timeout) viene gestita
tramite la tabella `CONNETTORI` del database GovPay, utilizzando la libreria `govpay-common`.

### Proprietà connettore

| Proprietà             | Descrizione                                                   | Default  |
|-----------------------|---------------------------------------------------------------|----------|
| `INVIA_TRACCIATO_ESITO` | Abilita produzione ZIP/CSV e invio email di esito           | `false`  |
| `FILE_SYSTEM_PATH`    | Directory di salvataggio del file ZIP                         | `/tmp`   |
| `EMAIL_INDIRIZZO`     | Destinatari email (separati da virgola)                       | —        |
| `EMAIL_ALLEGATO`      | Allega il file ZIP alla mail                                  | `false`  |
| `EMAIL_SUBJECT`       | Oggetto email personalizzato (se assente, generato dinamicamente) | —    |

## Parametri Batch

```properties
# Abilitazione batch
govpay.batch.enabled=true

# Identificativo del cluster per gestione multi-nodo
govpay.batch.cluster-id=GovPay-Maggioli-JPPA-Batch

# Timeout (minuti) per rilevare esecuzioni bloccate
govpay.batch.stale-threshold-minutes=120

# Numero di thread per elaborazione parallela domini
govpay.batch.thread-pool-size=5

# Dimensione chunk per step
govpay.batch.headers-chunk-size=1
govpay.batch.metadata-chunk-size=100
govpay.batch.payments-chunk-size=50

# Numero massimo di errori tollerati prima di fermare il job
govpay.batch.skip-limit=10

# Intervallo di scheduling (ms, default: 10 minuti)
scheduler.maggioliJppaNotificationJob.fixedDelayString=600000

# Ritardo iniziale prima della prima esecuzione (ms)
scheduler.initialDelayString=1
```

## Compilazione ed Esecuzione

### Compilazione

```bash
mvn clean install
```

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e devono essere forniti esternamente a runtime.
Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato:

| Database   | Driver                                                                                                      |
|------------|-------------------------------------------------------------------------------------------------------------|
| PostgreSQL | [postgresql-42.7.9.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.9/)                 |
| MySQL      | [mysql-connector-j-9.6.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.6.0/)          |
| Oracle     | [ojdbc11-23.26.1.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.1.0.0/)   |
| SQL Server | [mssql-jdbc-12.8.2.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/12.8.2.jre11/) |
| H2         | [h2-2.4.240.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.4.240/)                               |

Vedere anche [docker/jdbc-drivers/README.md](docker/jdbc-drivers/README.md) per istruzioni dettagliate.

### Esecuzione locale

Il jar utilizza `PropertiesLauncher` (layout ZIP) e richiede la proprietà `loader.path` per indicare
la directory contenente i driver JDBC:

```bash
# Avvio applicazione (modalità schedulata - profilo default)
java -Dloader.path=./jdbc-drivers -jar target/govpay-maggioli-jppa-batch.jar

# Esecuzione singola (profilo cron - esegue una volta e termina)
java -Dloader.path=./jdbc-drivers -jar target/govpay-maggioli-jppa-batch.jar --spring.profiles.active=cron
```

### Esecuzione con Docker

Con Docker la variabile d'ambiente `LOADER_PATH` viene impostata automaticamente dall'entrypoint
alla directory `/opt/jdbc-drivers`. I driver devono essere montati come volume:

```bash
docker run -v ./jdbc-drivers:/opt/jdbc-drivers linkitaly/govpay-maggioli-jppa
```

### Trigger Manuale via REST

```bash
# Avvio job
curl http://localhost:10001/api/batch/eseguiJob

# Forzare esecuzione anche se in corso
curl http://localhost:10001/api/batch/eseguiJob?force=true

# Stato corrente del batch
curl http://localhost:10001/api/batch/status
```

## Variabili d'Ambiente Docker

| Variabile                                  | Descrizione                                   | Default              |
|--------------------------------------------|-----------------------------------------------|----------------------|
| `GOVPAY_DB_TYPE`                           | Tipo database (`postgresql`, `mysql`, `oracle`, `sqlserver`) | — |
| `GOVPAY_DB_SERVER`                         | Host:porta del database                       | —                    |
| `GOVPAY_DB_NAME`                           | Nome del database                             | —                    |
| `GOVPAY_DB_USER`                           | Utente database                               | —                    |
| `GOVPAY_DB_PASSWORD`                       | Password database                             | —                    |
| `GOVPAY_DS_JDBC_LIBS`                      | Path directory driver JDBC                    | `/opt/jdbc-drivers`  |
| `GOVPAY_MAGGIOLI_JPPA_BATCH_USA_CRON`     | Modalità cron (esecuzione singola)            | `FALSE`              |
| `GOVPAY_MAGGIOLI_JPPA_BATCH_INTERVALLO_CRON` | Intervallo scheduler in minuti             | `120`                |
| `SPRING_MAIL_HOST`                         | Host server SMTP per invio email              | `localhost`          |
| `SPRING_MAIL_PORT`                         | Porta server SMTP                             | `25`                 |

## Script Database

Il progetto include script SQL per tutti i DBMS supportati:

```
src/main/resources/sql/{dbms}/
├── create-db.sql    # Creazione tabella JPPA_NOTIFICHE
├── delete-db.sql    # Pulizia dati
└── drop-db.sql      # Drop tabella
```

### DBMS supportati
- `postgresql` - PostgreSQL 9.6+
- `mysql` - MySQL 5.7+ / MariaDB 10.3+
- `oracle` - Oracle 11g+
- `sqlserver` - SQL Server 2016+
- `hsqldb` - HSQLDB (per sviluppo e test)

## Test

```bash
mvn test
```

## Note Tecniche

- **Java Version**: 21
- **Spring Boot**: 3.x
- **Spring Batch**: 5.x
- **OpenAPI Generator**: 7.10.0
- **Database**: PostgreSQL (prod), MySQL, Oracle, SQL Server, H2/HSQLDB (dev/test)
- **Build Tool**: Maven 3.6.3+

## Documentazione

- **[ChangeLog](ChangeLog)** - Storia completa delle modifiche e release
- **[Driver JDBC](docker/jdbc-drivers/README.md)** - Istruzioni download driver JDBC

## License

Questo progetto è distribuito sotto licenza GPL v3. Vedere il file [LICENSE](LICENSE) per i dettagli.

## Contatti

- **Progetto**: [GovPay Maggioli JPPA](https://github.com/link-it/govpay-maggioli-jppa)
- **Organizzazione**: [Link.it](https://www.link.it)
