IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'jppa_notifiche')
CREATE TABLE jppa_notifiche (
    id_rpt BIGINT NOT NULL,
    cod_dominio VARCHAR(35),
    CONSTRAINT pk_jppa_notifiche PRIMARY KEY (id_rpt)
);
