package it.govpay.maggioli.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing a Singolo Versamento
 */
@Entity
@Table(name = "SINGOLI_VERSAMENTI")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SingoloVersamento {

    @Id
    @Column(name = "id")
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "descrizione")
    private String descrizione;

    @Column(name = "contabilita")
    private String contabilita;

    @ManyToOne
    @JoinColumn(name = "id_versamento", nullable=false)
    @ToString.Exclude
    private Versamento versamento;
}
