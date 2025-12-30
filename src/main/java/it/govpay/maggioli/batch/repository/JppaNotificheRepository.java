package it.govpay.maggioli.batch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.govpay.maggioli.batch.entity.JppaNotifiche;

@Repository
public interface JppaNotificheRepository extends JpaRepository<JppaNotifiche, String> {

	/**
     * Delete all records from JPPA_NOTIFICHE
     */
    @Modifying
    @Query("DELETE FROM JppaNotifiche")
    void deleteAllRecords();

    /**
     * Check if Notifica already exists in temporary table
     */
    boolean existsByIdRptAndCodDominio(Long idRpt, String codDominio);

    /**
     * Find all distinct cod_dominio in JPPA_NOTIFICHE table (for partitioning)
     */
    @Query("SELECT DISTINCT n.codDominio FROM JppaNotifiche n ORDER BY n.codDominio")
    List<String> findDistinctCodDominio();

}
