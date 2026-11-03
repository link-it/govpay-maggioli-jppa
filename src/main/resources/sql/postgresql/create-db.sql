CREATE TABLE IF NOT EXISTS jppa_notifiche (
    id_rpt BIGINT NOT NULL,
    cod_dominio VARCHAR(35),
    CONSTRAINT pk_jppa_notifiche PRIMARY KEY (id_rpt)
);
