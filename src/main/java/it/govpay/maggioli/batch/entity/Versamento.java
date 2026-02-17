package it.govpay.maggioli.batch.entity;

import java.util.Set;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing a Versamento
 */
@Entity
@Table(name = "VERSAMENTI")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Versamento {

    @Id
    @Column(name = "id")
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "id_applicazione", nullable = false)
    private Long idApplicazione;

    @Column(name = "cod_versamento_ente", nullable= false, length = 35)
    private String codVersamentoEnte;

    @OneToMany(mappedBy = "versamento")
    @ToString.Exclude
    private Set<SingoloVersamento> singoliVersamenti;
}
