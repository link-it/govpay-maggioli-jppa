package it.govpay.maggioli.batch.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a JPPA configuration
 */
@Entity
@Table(name = "JPPA_CONFIG")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JppaConfig {

    @Id
    @Column(name = "cod_dominio", length = 35)
    private String codDominio;

    @Column(name = "cod_connettore", length = 255)
    private String connettore;

    @Column(name = "abilitato", nullable = false)
    @Builder.Default
    private Boolean abilitato = true;

    @Column(name = "data_ultima_rt")
    private Instant dataUltimaRt;

}
