IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'JPPA_NOTIFICHE')
CREATE TABLE JPPA_NOTIFICHE (
    id_rpt BIGINT NOT NULL,
    cod_dominio VARCHAR(35),
    CONSTRAINT pk_jppa_notifiche PRIMARY KEY (id_rpt)
);
