package it.govpay.maggioli.batch.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.maggioli.batch.dto.MaggioliHeadersBatch;
import it.govpay.maggioli.batch.entity.RPT;

@Repository
public interface RptRepository extends JpaRepository<RPT, Long> {
	public interface InfoProjection {
		Long getId();
	}

	List<InfoProjection> findByCodDominioAndCodEsitoPagamentoIn(String codDominio, List<Integer> codEsitoPagamento);

	List<InfoProjection> findByCodDominioAndCodEsitoPagamentoInAndDataMsgRicevutaAfter(String codDominio, List<Integer> codEsitoPagamento, Instant date);

    default MaggioliHeadersBatch.NotificaHeader infoConverter(InfoProjection info) {
    	return MaggioliHeadersBatch.NotificaHeader.builder()
                                                  .idRpt(info.getId())
                                                  .build();
    }

	default List<MaggioliHeadersBatch.NotificaHeader> findIdsByCodDominioAndCodEsitoPagamentoIn(String codDominio, List<Integer> codEsitoPagamento) {
		return findByCodDominioAndCodEsitoPagamentoIn(codDominio, codEsitoPagamento).stream()
				.map(this::infoConverter)
				.toList();
	}

	default List<MaggioliHeadersBatch.NotificaHeader> findIdsByCodDominioAndCodEsitoPagamentoInAndDataMsgRicevutaAfter(String codDominio, List<Integer> codEsitoPagamento, Instant date) {
		return findByCodDominioAndCodEsitoPagamentoInAndDataMsgRicevutaAfter(codDominio, codEsitoPagamento, date).stream()
				.map(this::infoConverter)
				.toList();
	}

	/**
     * Find all RTP con id in JPPA_NOTIFICHE per il dominio indicato
     */
    @Query("SELECT r FROM RPT r, JppaNotifiche n WHERE n.idRpt = r.id AND r.codDominio = :codDominio ORDER BY r.dataMsgRicevuta")
    List<RPT> findByNotificheOrderByDataMsgRicevuta(@Param("codDominio") String codDominio);
}
