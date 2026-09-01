-- =============================================================================
-- Migrazione metadati Spring Batch 5.x -> 6.0 per HSQLDB
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

-- Su HSQLDB la sequence e' emulata con una tabella IDENTITY.
ALTER TABLE BATCH_JOB_SEQ RENAME TO BATCH_JOB_INSTANCE_SEQ;
