#!/bin/bash

##############################################################################
# Script di Inizializzazione Database GovPay Maggioli JPPA
# Questo script inizializza il database eseguendo:
# 1. Liveness check (connessione TCP al database)
# 2. Readiness check (verifica esistenza delle tabelle Spring Batch e di
#    JPPA_NOTIFICHE)
# 3. Esecuzione degli script SQL delle sole tabelle che mancano
#
##############################################################################
# Niente tracing (set -x): scriverebbe nei log la password del database.
set -e

# Configurazione con valori di default
GOVPAY_MAGGIOLI_JPPA_POP_DB_SKIP=${GOVPAY_MAGGIOLI_JPPA_POP_DB_SKIP:-TRUE}
GOVPAY_MAGGIOLI_JPPA_DB_CHECK_TABLE=${GOVPAY_MAGGIOLI_JPPA_DB_CHECK_TABLE:-batch_job_execution_context}
GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_SKIP=${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_SKIP:-FALSE}
GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_SKIP=${GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_SKIP:-FALSE}
GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_MAX_RETRY=${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_MAX_RETRY:-30}
GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_SLEEP_TIME=${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_SLEEP_TIME:-2}
GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_CONNECT_TIMEOUT=${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_CONNECT_TIMEOUT:-5}
GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_MAX_RETRY=${GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_MAX_RETRY:-5}
GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_SLEEP_TIME=${GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_SLEEP_TIME:-2}

# Funzioni di logging
log_info() {
    echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_warn() {
    echo "[WARN] $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_error() {
    echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') - $1" >&2
}

# Salta se l'inizializzazione e' disabilitata
if [ "${GOVPAY_MAGGIOLI_JPPA_POP_DB_SKIP^^}" == "TRUE" ]; then
    log_info "Inizializzazione database saltata (GOVPAY_MAGGIOLI_JPPA_POP_DB_SKIP=TRUE)"
    exit 0
fi

log_info "========================================"
log_info "Inizializzazione Database GovPay Maggioli JPPA"
log_info "========================================"

# Estrazione server e porta da GOVPAY_DB_SERVER
IFS=':' read -r DB_HOST DB_PORT <<< "${GOVPAY_DB_SERVER}"

# Imposta porte di default se non specificate
if [ -z "${DB_PORT}" ] || [ "${DB_PORT}" == "${DB_HOST}" ]; then
    case "${GOVPAY_DB_TYPE}" in
        postgresql) DB_PORT=5432 ;;
        mysql|mariadb) DB_PORT=3306 ;;
        oracle) DB_PORT=1521 ;;
        *) DB_PORT=5432 ;;
    esac
fi

log_info "Tipo database: ${GOVPAY_DB_TYPE}"
log_info "Server database: ${DB_HOST}:${DB_PORT}"
log_info "Nome database: ${GOVPAY_DB_NAME}"

##############################################################################
# LIVENESS CHECK (Connessione TCP)
##############################################################################

if [ "${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_SKIP^^}" == "FALSE" ]; then
    log_info "Esecuzione liveness check (connessione TCP)..."
    RETRY=0
    DB_ALIVE=1

    while [ ${DB_ALIVE} -ne 0 ] && [ ${RETRY} -lt ${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_MAX_RETRY} ]; do
        # Con set -e un nc fallito fuori da un if chiuderebbe lo script al primo
        # tentativo, senza riprovare.
        if nc -w ${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_CONNECT_TIMEOUT} -z "${DB_HOST}" "${DB_PORT}"; then
            DB_ALIVE=0
        else
            DB_ALIVE=$?
        fi
        RETRY=$((RETRY + 1))

        if [ ${DB_ALIVE} -ne 0 ]; then
            log_info "Database non pronto, tentativo ${RETRY}/${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_MAX_RETRY}..."
            sleep ${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_SLEEP_TIME}
        fi
    done

    if [ ${DB_ALIVE} -ne 0 ]; then
        log_error "FATALE: Database non raggiungibile dopo ${GOVPAY_MAGGIOLI_JPPA_LIVE_DB_CHECK_MAX_RETRY} tentativi"
        exit 1
    fi

    log_info "Liveness check superato"
fi

##############################################################################
# COSTRUZIONE URL JDBC
##############################################################################

case "${GOVPAY_DB_TYPE}" in
    postgresql)
        JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
        START_TRANSACTION="START TRANSACTION;"
        ;;
    mysql|mariadb)
        JDBC_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
        START_TRANSACTION="START TRANSACTION;"
        ;;
    oracle)
        if [ "${GOVPAY_ORACLE_JDBC_URL_TYPE:-servicename}" == "servicename" ]; then
            JDBC_URL="jdbc:oracle:thin:@//${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
        else
            JDBC_URL="jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}:${GOVPAY_DB_NAME}"
        fi
        START_TRANSACTION=""
        ;;
    *)
        log_error "Tipo database non supportato: ${GOVPAY_DB_TYPE}"
        exit 1
        ;;
esac

# Aggiunta parametri di connessione se presenti
if [ -n "${GOVPAY_DS_CONN_PARAM}" ]; then
    if [[ "${JDBC_URL}" == *"?"* ]]; then
        JDBC_URL="${JDBC_URL}&${GOVPAY_DS_CONN_PARAM}"
    else
        JDBC_URL="${JDBC_URL}?${GOVPAY_DS_CONN_PARAM}"
    fi
fi

log_info "URL JDBC: ${JDBC_URL}"

##############################################################################
# CREAZIONE FILE RC SQLTOOL
##############################################################################

SQLTOOL_RC_FILE="/tmp/sqltool_maggioli_jppa.rc"
# Il file contiene la password del database: leggibile solo dall'utente e
# cancellato all'uscita dello script, insieme agli script SQL temporanei.
umask 077
trap 'rm -f "${SQLTOOL_RC_FILE}"; rm -rf /tmp/maggioli_jppa_sql' EXIT
cat > ${SQLTOOL_RC_FILE} <<EOSQLTOOL
urlid maggioli_jppa_db
url ${JDBC_URL}
username ${GOVPAY_DB_USER}
password ${GOVPAY_DB_PASSWORD}
driver ${GOVPAY_DS_DRIVER_CLASS}
transiso TRANSACTION_READ_COMMITTED
charset UTF-8
EOSQLTOOL

# Comando SqlTool, usato sia dal readiness check sia dalla creazione dello
# schema: va definito qui perche' il readiness check si puo' saltare.
INVOCAZIONE_CLIENT="-Dfile.encoding=UTF-8 -cp ${GOVPAY_DS_JDBC_LIBS}/*:/opt/hsqldb-${HSQLDB_FULLVERSION}/hsqldb/lib/sqltool.jar org.hsqldb.cmdline.SqlTool --rcFile=${SQLTOOL_RC_FILE} "

##############################################################################
# READINESS CHECK (Esistenza Tabelle)
##############################################################################

# Stampa il numero di tabelle con il nome indicato (0 o 1), ritentando se il
# database non risponde; esce con 1 se non ottiene una risposta.
conta_tabella() {
    local TABELLA="${1,,}"
    local CHECK_QUERY
    case "${GOVPAY_DB_TYPE}" in
        postgresql)
            CHECK_QUERY="SELECT count(*) FROM information_schema.tables WHERE LOWER(table_name)='${TABELLA}' AND LOWER(table_catalog)='${GOVPAY_DB_NAME,,}';"
            ;;
        mysql|mariadb)
            CHECK_QUERY="SELECT count(*) FROM information_schema.tables WHERE LOWER(table_name)='${TABELLA}' AND LOWER(table_schema)='${GOVPAY_DB_NAME,,}';"
            ;;
        oracle)
            # Oracle registra i nomi in maiuscolo: owner va confrontato con UPPER
            CHECK_QUERY="SELECT count(*) FROM all_tables WHERE LOWER(table_name)='${TABELLA}' AND UPPER(owner)='${GOVPAY_DB_USER^^}';"
            ;;
    esac

    local RETRY=0
    local TABLE_COUNT
    while [ ${RETRY} -lt ${GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_MAX_RETRY} ]; do
        TABLE_COUNT=$(java ${INVOCAZIONE_CLIENT} \
            --sql="${CHECK_QUERY}" \
            maggioli_jppa_db 2>/dev/null | tail -1 | tr -d ' \n\r')
        if [[ "${TABLE_COUNT}" =~ ^[0-9]+$ ]]; then
            echo "${TABLE_COUNT}"
            return 0
        fi
        RETRY=$((RETRY + 1))
        if [ ${RETRY} -lt ${GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_MAX_RETRY} ]; then
            log_info "Readiness check su ${TABELLA} fallito, tentativo ${RETRY}/${GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_MAX_RETRY}..." >&2
            sleep ${GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_SLEEP_TIME}
        fi
    done
    log_error "FATALE: Readiness check su ${TABELLA} fallito dopo ${GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_MAX_RETRY} tentativi"
    return 1
}

# Le tabelle Spring Batch possono gia' esistere perche' il database di GovPay
# e' condiviso con gli altri batch: si controllano separatamente da
# JPPA_NOTIFICHE e si crea solo cio' che manca.
CREA_SCHEMA_BATCH=TRUE
CREA_JPPA_NOTIFICHE=TRUE
if [ "${GOVPAY_MAGGIOLI_JPPA_READY_DB_CHECK_SKIP^^}" == "FALSE" ]; then
    log_info "Esecuzione readiness check (tabelle: ${GOVPAY_MAGGIOLI_JPPA_DB_CHECK_TABLE}, jppa_notifiche)..."

    NUM_BATCH=$(conta_tabella "${GOVPAY_MAGGIOLI_JPPA_DB_CHECK_TABLE}") || exit 1
    NUM_JPPA=$(conta_tabella jppa_notifiche) || exit 1
    log_info "Risultato readiness check: ${GOVPAY_MAGGIOLI_JPPA_DB_CHECK_TABLE}=${NUM_BATCH}, jppa_notifiche=${NUM_JPPA}"

    [ "${NUM_BATCH}" -gt 0 ] && CREA_SCHEMA_BATCH=FALSE
    [ "${NUM_JPPA}" -gt 0 ] && CREA_JPPA_NOTIFICHE=FALSE

    if [ "${CREA_SCHEMA_BATCH}" == "FALSE" ] && [ "${CREA_JPPA_NOTIFICHE}" == "FALSE" ]; then
        log_info "Database gia' inizializzato, esecuzione SQL saltata"
        exit 0
    fi
fi

##############################################################################
# ESECUZIONE SCRIPT SQL
##############################################################################

log_info "Inizializzazione database in corso..."

# Determina directory SQL applicativa (mariadb usa gli script mysql)
SQL_DIR="${GOVPAY_DB_TYPE}"
[ "${GOVPAY_DB_TYPE}" == "mariadb" ] && SQL_DIR="mysql"

# Schema dei metadati Spring Batch: file ufficiale della libreria, estratto in
# fase di packaging in /opt/sql/spring-batch e mai modificato. Ha un file per
# vendor, mariadb compreso, quindi qui si usa GOVPAY_DB_TYPE e non SQL_DIR.
BATCH_SCHEMA="/opt/sql/spring-batch/schema-${GOVPAY_DB_TYPE}.sql"

# Tracciabilita' di cosa contiene /opt/sql
if [ -f /opt/sql/VERSION ]; then
    log_info "Script SQL a bordo immagine:"
    grep -v '^#' /opt/sql/VERSION | grep -v '^$' | while read -r riga; do log_info "  ${riga}"; done
fi

# Verifica esistenza script SQL
for SQL_FILE in "${BATCH_SCHEMA}" "/opt/sql/${SQL_DIR}/create-db.sql"; do
    if [ ! -f "${SQL_FILE}" ]; then
        log_error "Script SQL non trovato: ${SQL_FILE}"
        log_error "File disponibili in /opt/sql:"
        ls -la /opt/sql/ 2>/dev/null || echo "  directory /opt/sql non trovata"
        if [ -d "/opt/sql/${SQL_DIR}" ]; then
            log_error "File in /opt/sql/${SQL_DIR}:"
            ls -la "/opt/sql/${SQL_DIR}/" 2>/dev/null
        fi
        if [ -d /opt/sql/spring-batch ]; then
            log_error "File in /opt/sql/spring-batch:"
            ls -la /opt/sql/spring-batch/ 2>/dev/null
        fi
        exit 1
    fi
done

# Copia script in posizione temporanea
mkdir -p /tmp/maggioli_jppa_sql
cp "/opt/sql/${SQL_DIR}"/*.sql /tmp/maggioli_jppa_sql/
cp "${BATCH_SCHEMA}" /tmp/maggioli_jppa_sql/
log_info "Script SQL copiati in /tmp/maggioli_jppa_sql/ (schema batch: $(basename "${BATCH_SCHEMA}"))"

# Applica trasformazioni specifiche per vendor
case "${GOVPAY_DB_TYPE}" in
    mysql|mariadb)
        log_info "Applicazione trasformazioni MySQL/MariaDB..."
        # Rimuove quote escaped negli statement COMMENT
        sed -i -e "/COMMENT/s%\\\\'% %g" /tmp/maggioli_jppa_sql/*.sql
        ;;
    oracle)
        log_info "Applicazione trasformazioni Oracle..."
        # Abilita modalita' raw per trigger e funzioni
        sed -i -r -e '/^CREATE( OR REPLACE)? (TRIGGER|FUNCTION|PROCEDURE)/i .' \
                  -e 's/^\/$/.\n:;/' /tmp/maggioli_jppa_sql/*.sql
        ;;
esac


# Esecuzione script SQL
log_info "Esecuzione script SQL in corso..."

SCRIPT_DA_ESEGUIRE=""
if [ "${CREA_SCHEMA_BATCH}" == "TRUE" ]; then
    log_info "Creazione delle tabelle Spring Batch"
    SCRIPT_DA_ESEGUIRE="${SCRIPT_DA_ESEGUIRE}\\i /tmp/maggioli_jppa_sql/schema-${GOVPAY_DB_TYPE}.sql"$'\n'
fi
if [ "${CREA_JPPA_NOTIFICHE}" == "TRUE" ]; then
    log_info "Creazione della tabella JPPA_NOTIFICHE"
    SCRIPT_DA_ESEGUIRE="${SCRIPT_DA_ESEGUIRE}\\i /tmp/maggioli_jppa_sql/create-db.sql"$'\n'
fi

# Con set -e un errore di SqlTool chiuderebbe lo script senza il messaggio
# d'errore: l'exit code si raccoglie con ||.
SQL_EXIT_CODE=0
java ${INVOCAZIONE_CLIENT} \
    --continueOnErr=false \
    maggioli_jppa_db <<EOSQL || SQL_EXIT_CODE=$?
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
${START_TRANSACTION}
${SCRIPT_DA_ESEGUIRE}COMMIT;
EOSQL

if [ ${SQL_EXIT_CODE} -eq 0 ]; then
    log_info "========================================"
    log_info "Inizializzazione database completata con successo"
    log_info "========================================"
else
    log_error "========================================"
    log_error "Inizializzazione database fallita con codice d'uscita: ${SQL_EXIT_CODE}"
    log_error "========================================"
fi

exit ${SQL_EXIT_CODE}
