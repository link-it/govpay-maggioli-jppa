package it.govpay.maggioli.batch.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing a Receipt
 */
@Entity
@Table(name = "RPT")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RPT {

    @Id
    @Column(name = "id")
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @ManyToOne()
    @JoinColumn(name = "id_versamento", nullable = false)
    @ToString.Exclude
    private Versamento versamento;

    @Column(name = "stato", nullable = false, length = 35)
    private String stato;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "ccp", nullable = false, length = 35)
    private String ccp;

    @Column(name = "data_msg_ricevuta", nullable = false)
    private Instant dataMsgRicevuta;

    @Column(name = "cod_esito_pagamento")
    private Integer codEsitoPagamento;
    
    @Column(name = "xml_rt")
    private byte[] xmlRt;
}
