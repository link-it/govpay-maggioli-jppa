package it.govpay.maggioli.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a info to be notify JPPA
 */
@Entity
@Table(name = "JPPA_NOTIFICHE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JppaNotifiche {

    @Id
    @Column(name = "id_rpt")
    private Long idRpt;

    @Column(name = "cod_dominio", length = 35)
    private String codDominio;

}
