package it.govpay.maggioli.batch.entity;

import java.util.Set;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a Receipt
 */
@Entity
@Table(name = "VERSAMENTI")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Versamento {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "id_applicazione", nullable = false)
    private Long idApplicazione;

    @Column(name = "cod_versamento_ente", nullable= false, length = 35)
    private String codVersamentoEnte;

    @OneToMany(mappedBy = "versamento")
    private Set<SingoloVersamento> singoliVersamenti;
}
