package it.govpay.maggioli.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a Receipt
 */
@Entity
@Table(name = "SINGOLI_VERSAMENTI")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SingoloVersamento {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "descrizione")
    private String descrizione;

    @Column(name = "contabilita")
    private String contabilita;

    @ManyToOne
    @JoinColumn(name = "id_versamento", nullable=false)
    private Versamento versamento;
}
