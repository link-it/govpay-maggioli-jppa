-- =============================================================================
-- Migrazione metadati Spring Batch 5.x -> 6.0 per SQL Server
--
-- Spring Batch 6.0 ha rinominato la sequence del job instance:
--   BATCH_JOB_SEQ -> BATCH_JOB_INSTANCE_SEQ
-- (org.springframework.batch.core.repository.support.JobRepositoryFactoryBean,
--  default: <table-prefix>JOB_INSTANCE_SEQ).
--
-- Da eseguire UNA SOLA VOLTA sui database creati con una versione precedente
-- del batch, dove le tabelle esistono gia'. Le installazioni nuove usano
-- direttamente tabelle_batch-create.sql, che contiene il nome nuovo.
--
-- Il vecchio nome resta utilizzabile senza migrazione impostando la variabile
-- d'ambiente SPRING_BATCH_JDBC_SCHEMA_LEGACY=TRUE (o la property di sistema
-- spring.batch.jdbc.schema.legacy=TRUE), sconsigliato perche' deprecato.
-- =============================================================================

EXEC sp_rename 'BATCH_JOB_SEQ', 'BATCH_JOB_INSTANCE_SEQ';

-- NOTA: la migrazione ufficiale Spring Batch 6.0 per SQL Server converte anche
-- tutte le colonne VARCHAR in NVARCHAR. Non applicarla: l'SQLServerDialect di
-- Hibernate mappa String su varchar (salvo @Nationalized), quindi con
-- spring.jpa.hibernate.ddl-auto=validate la conversione farebbe fallire l'avvio.
