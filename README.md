<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - Maggioli JPPA

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-maggioli-jppa&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-maggioli-jppa)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-maggioli-jppa?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-maggioli-jppa)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-maggioli-jppa/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Flink-it%2Fgovpay-bom%2Fmain%2Fpom.xml&query=%2F%2F*%5Blocal-name()%3D'spring-boot.version'%5D&label=Spring%20Boot&color=brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

Batch Spring Boot che notifica al gateway pagamenti Maggioli JPPA le ricevute di pagamento registrate in GovPay e, se richiesto, produce un tracciato di esito in CSV e lo invia per email.

## Sommario

Il batch esegue un job Spring Batch, `maggioliJppaNotificationJob`, sul database di GovPay. Per ogni dominio abilitato nella tabella `jppa_config` cerca le ricevute di pagamento (RT) positive arrivate dopo l'ultima già notificata e le invia, una per una, all'API REST del gateway Maggioli JPPA:

| Chiamata | Quando | Cosa invia | Cosa riceve |
|----------|--------|------------|-------------|
| `POST <URL>/rest/login` | prima di ogni notifica | `idMessaggio` (UUID), `identificativoEnte` (codice del dominio), `username` e `password` del connettore | token, usato come `Authorization: Bearer <token>` nella chiamata successiva |
| `POST <URL>/rest/notifiche/v2/pagamenti` | per ogni ricevuta | `identificativoDominioEnteCreditore`, `base64Ricevuta` (XML della RT di `rpt.xml_rt` in Base64) e `datiAccertamento`, ricavati dalla contabilità delle voci della pendenza (`singoli_versamenti.contabilita`) | `esito`, `warningMessages`, `errorMessages` |

`<URL>`, utente e password non sono proprietà del batch: sono nel connettore di GovPay indicato da `jppa_config.cod_connettore` (vedi [Connettore Maggioli JPPA](#connettore-maggioli-jppa)). Il client è generato dalla specifica `src/main/resources/openapi/maggioli-api.json` (API Gateway Pagamenti di Maggioli, versione 4.44.0), di cui il batch usa solo le due operazioni sopra.

Alla fine di ogni dominio il batch aggiorna `jppa_config.data_ultima_rt` con la data dell'ultima ricevuta inviata e, se il connettore lo prevede, scrive un tracciato CSV compresso con l'esito di ogni notifica e lo spedisce per email. Login e notifiche sono registrati nel Giornale degli Eventi.

Il job si avvia da uno scheduler interno (modalità di default), una sola volta con il profilo `cron`, oppure a richiesta con l'API REST descritta in [Avvio manuale via REST](#avvio-manuale-via-rest). Le esecuzioni sono coordinate tra più nodi tramite i metadati Spring Batch sul database.

Storico delle modifiche nel [ChangeLog](ChangeLog) e nelle [note di rilascio](RELEASE_NOTES.md).

## Integrazione con GovPay

| Componente | Quando serve | Obbligatorio | Ruolo |
|------------|--------------|--------------|-------|
| Database di GovPay | runtime | sì | Legge `jppa_config` (domini abilitati, connettore, data dell'ultima RT notificata), `rpt` (ricevute con esito `0` o `2`), `versamenti`, `singoli_versamenti`, `connettori` e `configurazione`; aggiorna `jppa_config.data_ultima_rt`. `jppa_config` e le altre tabelle sono create dagli script di installazione di GovPay |
| Tabella `jppa_notifiche` | runtime | sì | Tabella di lavoro del batch, svuotata a ogni esecuzione. Lo script di creazione è in `src/main/resources/sql/<database>/create-db.sql` e nel pacchetto `sql.zip` (vedi [Compilazione](#compilazione)) |
| Tabelle Spring Batch | runtime | sì | Metadati delle esecuzioni (`batch_job_*`, `batch_step_*`), usati anche per evitare esecuzioni concorrenti tra nodi. Lo schema è in `sql.zip` e l'immagine Docker può crearlo all'avvio |
| [govpay-bom](https://github.com/link-it/govpay-bom) | build | sì | Parent Maven: versioni delle dipendenze e dei plugin, profilo `dist` |
| [govpay-common](https://github.com/link-it/govpay-common) | build | sì | Libreria comune: connettori, entità di anagrafica, coordinamento dei job, API REST del batch, client GDE, invio email, transaction id e livelli di log da database |
| Gateway pagamenti Maggioli JPPA | runtime | sì | Riceve login e notifiche di pagamento. URL e credenziali sono nel connettore del dominio |
| GovPay (monolite) | runtime | sì | Riceve le RT da pagoPA e le registra in `rpt`; è la sorgente delle ricevute che il batch notifica |
| [govpay-console-api](https://github.com/link-it/govpay-console-api) | runtime | no | Gestisce dalla console il connettore Maggioli JPPA del dominio (`jppa_config` e `connettori`). Può avviare il batch tramite l'API REST se il batch è aggiunto al catalogo delle operazioni (`govpay.operazioni.catalogo`); nel catalogo di default non c'è |
| [govpay-gde-api](https://github.com/link-it/govpay-gde-api) | runtime | no | Riceve gli eventi di login e di notifica (componente `API_MAGGIOLI_JPPA`), se il connettore `govpay_gde_api` è abilitato in GovPay. Un GDE non raggiungibile non blocca il batch |
| Server SMTP | runtime | no | Spedisce il tracciato di esito, se `INVIA_TRACCIATO_ESITO` è attivo nel connettore. Il server si configura nella riga `mail_batch` della tabella `configurazione` di GovPay, non con proprietà del batch |

Le modifiche ai livelli di log fatte dalla console (riga `log_level` della tabella `configurazione`) raggiungono il batch entro l'intervallo di refresh, senza riavvio.

## Compilazione

Requisiti: JDK 21 e Maven 3.8.1 o successivo.

```bash
mvn clean install
```

Il risultato è il fat jar `target/govpay-maggioli-jppa-batch.jar`. La build genera il client dell'API Maggioli dalla specifica OpenAPI, esegue i test e l'analisi OWASP Dependency-Check delle dipendenze; per escludere l'analisi OWASP, per esempio in sviluppo:

```bash
mvn clean install -Dowasp.phase=none
```

Per produrre anche `target/sql.zip`, con lo schema Spring Batch per ogni database, gli script di svecchiamento dei metadati e lo script di `jppa_notifiche`, usato dall'immagine Docker e allegato alle release:

```bash
mvn clean install -Pjar,dist
```

Il profilo `jar` va indicato insieme a `dist`: è quello che lascia i driver JDBC fuori dal jar.

## Configurazione

Le proprietà si impostano in `application.properties`, da riga di comando (`-D<proprietà>=<valore>`) o con le variabili d'ambiente equivalenti di Spring Boot (es. `govpay.batch.thread-pool-size` → `GOVPAY_BATCH_THREADPOOLSIZE`, `scheduler.maggioliJppaNotificationJob.fixedDelayString` → `SCHEDULER_MAGGIOLIJPPANOTIFICATIONJOB_FIXEDDELAYSTRING`). Con l'immagine Docker si usano anche le variabili descritte in [Esecuzione con Docker](#esecuzione-con-docker).

### Elaborazione

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.batch.thread-pool-size` | `5` | Thread per l'elaborazione parallela: domini letti in parallelo nello step 2 e partizioni (un dominio ciascuna) elaborate in parallelo nello step 3 |
| `govpay.batch.chunk-size` | `100` | Elementi per transazione negli step 2 (domini) e 3 (ricevute) |
| `govpay.batch.max-retries` | `5` | Tentativi per una notifica che fallisce con errore di rete o HTTP diverso da 400, con attesa crescente da 2 a 10 secondi |
| `scheduler.maggioliJppaNotificationJob.fixedDelayString` | `600000` | Intervallo tra la fine di un'esecuzione e l'avvio della successiva, in ms (10 minuti; 120 minuti nel container) |
| `scheduler.initialDelayString` | `1` | Ritardo della prima esecuzione dopo l'avvio, in ms |

### Esecuzione e cluster

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.batch.cluster-id` | `GovPay-Maggioli-JPPA-Batch` | Identificativo del nodo, registrato nelle esecuzioni e negli eventi del GDE; nel container è l'indirizzo IP |
| `govpay.batch.stale-threshold-minutes` | `120` | Minuti senza aggiornamenti dopo cui un'esecuzione in corso è considerata bloccata e può essere superata |
| `govpay.batch.time-zone` | `Europe/Rome` | Fuso orario della JVM e dei calcoli sulle esecuzioni |
| `govpay.batch.job-repository.jdbc.enabled` | `true` | Metadati Spring Batch su database (da `govpay-common`); senza, il coordinamento tra nodi non funziona |
| `spring.batch.job.enabled` | `false` | Avvio automatico dei job da parte di Spring Boot: resta `false`, il job è avviato dallo scheduler o dal profilo `cron` |
| `spring.profiles.active` | `default` | `cron` per eseguire il job una volta e terminare (vedi [Esecuzione](#esecuzione)) |
| `server.port` | `8080` (`10001` nel container) | Porta HTTP dell'API REST del batch e degli endpoint actuator |
| `spring.main.web-application-type` | `servlet` (`none` nel container con il profilo `cron`) | `none` non avvia il server HTTP |
| `spring.application.name` | `govpay-maggioli-jppa-batch` | Nome dell'applicazione nei log |
| `spring.main.banner-mode` | `off` | Banner di Spring Boot all'avvio |
| `spring.jackson.default-property-inclusion` | `non_null` | Esclude i campi nulli dal JSON |
| `spring.jackson.time-zone` | `Europe/Rome` | Fuso orario delle date nel JSON |

### Connettore Maggioli JPPA

URL e credenziali del gateway Maggioli non sono proprietà del batch: per ogni dominio la riga di `jppa_config` indica il codice del connettore (`cod_connettore`), le cui proprietà sono nella tabella `connettori` di GovPay e si gestiscono dalla console.

| Proprietà del connettore | Default | Descrizione |
|--------------------------|---------|-------------|
| `URL` | - | URL base dell'API Maggioli (a cui si aggiungono `/rest/login` e `/rest/notifiche/v2/pagamenti`) |
| `HTTPUSER` | - | Utente inviato nel corpo della chiamata di login |
| `HTTPPASSW` | - | Password inviata nel corpo della chiamata di login |
| `TIPOSSL`, `SSLTYPE`, `SSLKSTYPE`, `SSLKSLOCATION`, `SSLKSPASSWD`, `SSLPKEYPASSWD`, `SSLTSTYPE`, `SSLTSLOCATION`, `SSLTSPASSWD` | - | Configurazione TLS (keystore e truststore), gestita da `govpay-common` |
| `CONNECTION_TIMEOUT`, `READ_TIMEOUT` | quelli di `govpay-common` | Timeout delle chiamate, in ms |
| `INVIA_TRACCIATO_ESITO` | `false` | Produce il tracciato di esito (ZIP con un CSV) e lo spedisce per email |
| `FILE_SYSTEM_PATH` | `/tmp` | Directory in cui scrivere lo ZIP (creata se manca) |
| `EMAIL_INDIRIZZO` | - | Destinatari del tracciato, separati da virgola: il primo in `To`, gli altri in `Cc` |
| `EMAIL_ALLEGATO` | `false` | Allega lo ZIP all'email; altrimenti l'email contiene solo il riepilogo |
| `EMAIL_SUBJECT` | `Pagamenti inviati al servizio Maggioli JPPA al <data ultima RT>` | Oggetto dell'email |

L'autenticazione HTTP Basic eventualmente configurata nel connettore non viene usata: il batch la sostituisce con il login e il token Bearer. L'email parte solo se l'invio è abilitato nella riga `mail_batch` della tabella `configurazione`.

### Client HTTP e GDE

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.client.cache.enabled` | `false` | Carica i connettori all'avvio e li tiene in cache; la cache si svuota con `GET /api/batch/cache/clear` |
| `govpay.client.async.core-pool-size` | `10` | Thread minimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.max-pool-size` | `50` | Thread massimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.queue-capacity` | `100` | Eventi in coda in attesa di invio |
| `govpay.client.async.thread-name-prefix` | `async-http-` | Prefisso dei thread di invio |

L'invio al GDE si configura con il connettore `govpay_gde_api` e con la sezione del Giornale degli Eventi della tabella `configurazione` di GovPay.

### Database

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `spring.datasource.url` | - | URL JDBC del database di GovPay (obbligatoria) |
| `spring.datasource.username` | - | Utente del database |
| `spring.datasource.password` | - | Password del database |
| `spring.datasource.driver-class-name` | dedotto dall'URL | Classe del driver JDBC |
| `spring.jpa.database-platform` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.properties.hibernate.dialect` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Gestione dello schema: all'avvio Hibernate verifica che le tabelle mappate esistano con le colonne attese, senza modificarle |
| `spring.datasource.hikari.minimum-idle` | `10` (`2` nel container) | Connessioni minime inattive del pool |
| `spring.datasource.hikari.maximum-pool-size` | `10` | Connessioni massime del pool |
| `spring.datasource.hikari.connection-timeout` | `30000` (`20000` nel container) | Attesa massima di una connessione, in ms |
| `spring.datasource.hikari.idle-timeout` | `600000` (`10000` nel container) | Tempo dopo cui una connessione inattiva viene chiusa, in ms |
| `spring.datasource.hikari.max-lifetime` | `1800000` (`600000` nel container) | Durata massima di una connessione, in ms |

I driver JDBC non sono inclusi nel jar: vedi [Esecuzione](#esecuzione).

### Logging

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `logging.file.name` | - (solo console) | File di log; nel container `/var/log/govpay-maggioli-jppa/govpay-maggioli-jppa.log` |
| `logging.level.it.govpay` | `INFO` | Livello di log dei package GovPay |
| `logging.pattern.level` | `%5p [%X{transactionId:-},%X{correlationId:-}]` | Aggiunge a ogni riga transaction id e correlation id dell'esecuzione: il correlation id è quello della richiesta di avvio manuale, oppure generato per gli avvii schedulati |
| `govpay.logging.tracciatura.enabled` | `true` | Transaction id e correlation id nei log e nelle chiamate in uscita (da `govpay-common`) |
| `govpay.logging.livelli-dinamici.enabled` | `true` | Livelli di log letti dalla riga `log_level` della tabella `configurazione` |
| `govpay.logging.livelli-dinamici.intervallo-refresh` | `60s` (variabile `LOG_LEVEL_REFRESH_INTERVAL`) | Intervallo di rilettura dei livelli dal database; `0` li legge solo all'avvio |
| `govpay.logging.livelli-dinamici.logger-gestiti[0]` | `it.govpay` | Prefissi dei logger modificabili da database |

### Monitoraggio

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `management.server.port` | porta del servizio | Porta dedicata agli endpoint actuator |
| `management.endpoints.web.base-path` | `/actuator` | Base path degli endpoint actuator |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` (variabile `MANAGEMENT_ENDPOINTS`) | Endpoint actuator esposti |
| `management.endpoint.health.show-details` | `when_authorized` | Dettagli dell'health: il servizio non ha autenticazione, quindi restituisce solo lo stato |
| `management.health.db.enabled` | `true` | Include la connessione al database nell'health |
| `management.metrics.tags.application` | `govpay-maggioli-jppa-batch` | Tag che identifica il servizio nelle metriche |

## Esecuzione

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e vanno forniti a runtime. Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato (versioni gestite da `govpay-bom`):

| Database   | Driver |
|------------|--------|
| PostgreSQL | [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar) |
| MySQL / MariaDB | [mysql-connector-j-26.10.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar) |
| Oracle     | [ojdbc11-23.26.3.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar) |
| SQL Server | [mssql-jdbc-13.6.1.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.6.1.jre11/mssql-jdbc-13.6.1.jre11.jar) |
| H2         | [h2-2.5.252.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252.jar) |

### Schema del database

Prima del primo avvio, nel database di GovPay devono esistere:

- le tabelle dei metadati Spring Batch, create con lo script `sql/spring-batch/schema-<database>.sql` di `sql.zip`;
- la tabella `jppa_notifiche`, creata con lo script `sql/<database>/create-db.sql` di `sql.zip` (o di `src/main/resources/sql/`).

L'immagine Docker esegue gli script delle tabelle che mancano se `GOVPAY_MAGGIOLI_JPPA_POP_DB_SKIP=FALSE`. Gli script in `sql/cleanup/` cancellano i metadati delle esecuzioni vecchie e vanno pianificati periodicamente.

### Jar standalone

Il jar usa `PropertiesLauncher`: la proprietà `loader.path` indica la directory dei driver JDBC. Configurazione minima per PostgreSQL, con lo scheduler interno:

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -jar target/govpay-maggioli-jppa-batch.jar
```

Modalità di esecuzione:

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (nessun profilo) | Il processo resta attivo ed esegue il job ogni `scheduler.maggioliJppaNotificationJob.fixedDelayString` ms; espone API REST e actuator |
| Esecuzione singola | `--spring.profiles.active=cron` | Esegue il job una volta e termina: la pianificazione è affidata a un cron esterno. Il server HTTP parte comunque, sulla porta `server.port`, se non si aggiunge `-Dspring.main.web-application-type=none`. L'exit code è `0` anche se il job fallisce o è già in esecuzione su un altro nodo, `1` solo se non si riesce a superare un'esecuzione bloccata: l'esito va letto nei log |

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -Dspring.main.web-application-type=none \
     -jar target/govpay-maggioli-jppa-batch.jar --spring.profiles.active=cron
```

## Esecuzione con Docker

L'immagine è pubblicata su Docker Hub come [linkitaly/govpay-maggioli-jppa](https://hub.docker.com/r/linkitaly/govpay-maggioli-jppa) (release) e `linkitaly/govpay-maggioli-jppa-dev` (sviluppo, a ogni push su `main`). Con lo scheduler interno il servizio risponde sulla porta `10001`; i driver JDBC si montano in `/opt/jdbc-drivers`.

```bash
docker run -d --name govpay-maggioli-jppa -p 10001:10001 \
  -v ./jdbc-drivers:/opt/jdbc-drivers \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=postgres:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=<password> \
  linkitaly/govpay-maggioli-jppa
```

Variabili d'ambiente dell'immagine, oltre alle proprietà Spring Boot in forma di variabile (es. `GOVPAY_BATCH_THREADPOOLSIZE=10`):

| Variabile | Obbligatoria | Default | Descrizione |
|-----------|--------------|---------|-------------|
| `GOVPAY_DB_TYPE` | sì | - | Tipo di database: `postgresql`, `mysql`, `mariadb`, `oracle` (SQL Server non è supportato dall'immagine) |
| `GOVPAY_DB_SERVER` | sì | - | Server del database, `host` o `host:porta` (porta di default del database se omessa) |
| `GOVPAY_DB_NAME` | sì | - | Nome del database (per Oracle, service name o SID) |
| `GOVPAY_DB_USER` | sì | - | Utente del database |
| `GOVPAY_DB_PASSWORD` | sì | - | Password del database (se manca l'avvio prosegue con un avviso) |
| `GOVPAY_DS_CONN_PARAM` | no | - | Parametri aggiunti all'URL JDBC (es. `zeroDateTimeBehavior=convertToNull` per MySQL) |
| `GOVPAY_DS_JDBC_LIBS` | no | `/opt/jdbc-drivers` | Directory dei driver JDBC |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | no | `servicename` | Formato dell'URL Oracle: `servicename` o `sid` |
| `ORACLE_TNS_ADMIN` | no | - | Directory dei file TNS di Oracle |
| `GOVPAY_MAGGIOLI_JPPA_MIN_POOL` | no | `2` | Connessioni minime inattive del pool |
| `GOVPAY_MAGGIOLI_JPPA_MAX_POOL` | no | `10` | Connessioni massime del pool |
| `GOVPAY_MAGGIOLI_JPPA_BATCH_USA_CRON` | no | - | `true`, `si`, `yes` o `1`: esecuzione singola con il profilo `cron`, senza server HTTP; il container termina a fine job e va pianificato dall'esterno (es. CronJob Kubernetes). Altrimenti scheduler interno |
| `GOVPAY_MAGGIOLI_JPPA_BATCH_INTERVALLO_CRON` | no | `120` | Minuti tra un'esecuzione e l'altra con lo scheduler interno, convertiti in `SCHEDULER_MAGGIOLIJPPANOTIFICATIONJOB_FIXEDDELAYSTRING`. Un valore non numerico o zero blocca l'avvio |
| `SCHEDULER_MAGGIOLIJPPANOTIFICATIONJOB_FIXEDDELAYSTRING` | no | - | Intervallo in ms; se impostata ha la precedenza su `GOVPAY_MAGGIOLI_JPPA_BATCH_INTERVALLO_CRON`, che viene ignorata con un avviso |
| `SERVER_PORT` | no | `10001` | Porta HTTP con lo scheduler interno |
| `MANAGEMENT_SERVER_PORT` | no | porta del servizio | Porta dedicata agli endpoint actuator |
| `MANAGEMENT_ENDPOINTS` | no | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `LOG_LEVEL_REFRESH_INTERVAL` | no | `60s` | Intervallo di rilettura dei livelli di log dal database |
| `GOVPAY_MAGGIOLI_JPPA_POP_DB_SKIP` | no | `TRUE` | `FALSE` crea all'avvio le tabelle che mancano: lo schema Spring Batch se non esiste la tabella `GOVPAY_MAGGIOLI_JPPA_DB_CHECK_TABLE`, `jppa_notifiche` se non esiste. Le due verifiche sono separate perché le tabelle Spring Batch possono essere già state create da un altro batch GovPay |
| `GOVPAY_MAGGIOLI_JPPA_DB_CHECK_TABLE` | no | `batch_job_execution_context` | Tabella che indica che lo schema Spring Batch è già presente |
| `GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_SKIP` | no | `FALSE` | Salta l'attesa della porta del database prima dell'inizializzazione |
| `GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_MAX_RETRY` | no | `30` | Tentativi di connessione alla porta del database |
| `GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_CONNECT_TIMEOUT` | no | `5` | Timeout di ogni tentativo di connessione, in secondi |
| `GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_SKIP` | no | `FALSE` | Salta le verifiche delle tabelle ed esegue comunque entrambi gli script |
| `GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_MAX_RETRY` | no | `5` | Tentativi di ciascuna verifica delle tabelle |
| `GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_MAGGIOLI_JPPA_JVM_MAX_RAM_PERCENTAGE` | no | `80` | `-XX:MaxRAMPercentage` della JVM |
| `GOVPAY_MAGGIOLI_JPPA_JVM_INITIAL_RAM_PERCENTAGE` | no | - | `-XX:InitialRAMPercentage` della JVM |
| `GOVPAY_MAGGIOLI_JPPA_JVM_MIN_RAM_PERCENTAGE` | no | - | `-XX:MinRAMPercentage` della JVM |
| `GOVPAY_MAGGIOLI_JPPA_JVM_MAX_METASPACE_SIZE` | no | - | `-XX:MaxMetaspaceSize` della JVM |
| `GOVPAY_MAGGIOLI_JPPA_JVM_MAX_DIRECT_MEMORY_SIZE` | no | - | `-XX:MaxDirectMemorySize` della JVM |
| `JAVA_OPTS` | no | - | Opzioni aggiuntive della JVM |

Il Dockerfile imposta anche `GOVPAY_BATCH_THREAD_POOL_SIZE=5`, `GOVPAY_BATCH_CHUNK_SIZE=100` e `GOVPAY_BATCH_MAX_RETRIES=5` (uguali ai default), i parametri Hikari indicati in [Database](#database) e `LOGGING_FILE_NAME`.

L'identificativo del nodo (`govpay.batch.cluster-id`) è impostato dall'entrypoint, con la variabile `GOVPAY_BATCH_CLUSTER_ID`, all'indirizzo IP del container o al suo hostname se l'IP non si ricava: un valore passato dall'esterno viene sovrascritto. Le altre variabili che compaiono negli script dell'immagine (es. `INTERVALLO_MINUTI`, `HSQLDB_SHA256`) sono interne e non vanno impostate. I messaggi dell'entrypoint e della JVM finiscono nei log del container (`docker logs`).

Costruzione dell'immagine in locale con `docker/build_image.sh` (`-l <jar>` per un jar locale, dopo `mvn -Pjar,dist`), scansione delle vulnerabilità e pacchetti inclusi sono descritti in [docker/SCANSIONE.md](docker/SCANSIONE.md); i driver in [docker/jdbc-drivers/README.md](docker/jdbc-drivers/README.md).

## Monitoraggio

Con lo scheduler interno gli endpoint actuator rispondono di default sulla porta del servizio (`8080` con il jar, `10001` nel container):

```bash
curl http://localhost:10001/actuator/health       # stato, compresa la connessione al database
curl http://localhost:10001/actuator/prometheus   # metriche in formato Prometheus
```

Per spostarli su una porta dedicata impostare `management.server.port` (nel container `MANAGEMENT_SERVER_PORT`). L'endpoint `loggers`, che permette di cambiare i livelli di log a caldo, non è esposto di default perché il servizio non ha autenticazione: abilitarlo solo insieme a una porta dedicata e non raggiungibile dall'esterno, per esempio `MANAGEMENT_SERVER_PORT=9090` e `MANAGEMENT_ENDPOINTS=health,info,metrics,prometheus,loggers`. In alternativa i livelli si cambiano dalla console, tramite la tabella `configurazione`.

Con il profilo `cron` e senza server HTTP l'esito si ricava dai log.

## Avvio manuale via REST

Con lo scheduler interno il batch espone, sotto `/api/batch`, l'API comune ai batch GovPay (`AbstractBatchController` di `govpay-common`). Non ha autenticazione: va esposta solo sulla rete interna.

| Metodo e path | Descrizione |
|---------------|-------------|
| `GET /api/batch/run` | Avvia il job in modo asincrono (`202`, oppure `409` se è già in esecuzione) |
| `GET /api/batch/run?force=true` | Avvia il job anche se risulta un'esecuzione in corso |
| `GET /api/batch/status` | Stato corrente del job |
| `GET /api/batch/lastExecution` | Ultima esecuzione completata |
| `GET /api/batch/nextExecution` | Prossima esecuzione stimata |
| `GET /api/batch/info` | Nome e descrizione del batch |
| `GET /api/batch/executions` | Elenco paginato delle esecuzioni |
| `GET /api/batch/executions/{executionId}` | Dettaglio di un'esecuzione |
| `DELETE /api/batch/executions/{executionId}` | Richiede l'arresto di un'esecuzione |
| `GET /api/batch/cache/clear` | Svuota la cache dei connettori |

```bash
curl http://localhost:10001/api/batch/run
curl http://localhost:10001/api/batch/status
```

## Elaborazione

Il job `maggioliJppaNotificationJob` ha tre step:

1. **`cleanupStep`** (`CleanupJppaNotificheTasklet`): svuota `jppa_notifiche`.
2. **`maggioliHeadersAcquisitionStep`**, multi-thread:
   - **Reader** (`MaggioliJppaHeadersReader`): legge da `jppa_config` i domini con `abilitato` vero;
   - **Processor** (`MaggioliJppaHeadersProcessor`): per ogni dominio cerca in `rpt` le ricevute con `cod_esito_pagamento` `0` (eseguito) o `2` (parzialmente eseguito) e `data_msg_ricevuta` successiva a `jppa_config.data_ultima_rt`. Se `data_ultima_rt` è vuota le prende tutte: alla prima esecuzione su un dominio il batch notifica l'intero storico;
   - **Writer** (`MaggioliJppaHeadersWriter`): inserisce gli id delle RPT in `jppa_notifiche`.
3. **`maggioliSendNotificationStep`**, partizionato per dominio (`DominioPartitioner`; i domini senza connettore sono saltati con un avviso):
   - **Reader** (`SendNotificationReader`): legge le RPT del dominio presenti in `jppa_notifiche`, con pendenza e voci, in ordine di `data_msg_ricevuta`;
   - **Processor** (`SendNotificationProcessor`): esegue login e notifica descritti nel [Sommario](#sommario). Una risposta HTTP 400 non viene ritentata e la ricevuta finisce nel tracciato con esito `ERRORE_INVIO`; gli altri errori di rete o HTTP della notifica sono ritentati fino a `govpay.batch.max-retries` volte, poi fanno fallire la partizione; un login fallito (errore o risposta senza token) la fa fallire subito, senza nuovi tentativi;
   - **Writer** (`SendNotificationWriter`): scrive una riga del CSV per ricevuta (`idDominio`, `iuv`, `cpp`, `esito`, `warnings`, `errors`); a fine partizione aggiorna `jppa_config.data_ultima_rt` con la data dell'ultima ricevuta elaborata, chiude lo ZIP (`GOVPAY_<dominio>_<data>_<n>.zip`) e spedisce l'email.

Una ricevuta elaborata, anche con esito `ERRORE_INVIO` o con un esito negativo restituito da Maggioli, non viene più riproposta: `data_ultima_rt` avanza comunque. Per rinviarla va riportata indietro `data_ultima_rt` del dominio.

## Licenza

GovPay Maggioli JPPA è distribuito con licenza [GNU General Public License v3.0](LICENSE).
