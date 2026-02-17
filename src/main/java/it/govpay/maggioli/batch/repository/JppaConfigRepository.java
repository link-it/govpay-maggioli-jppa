package it.govpay.maggioli.batch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.govpay.maggioli.batch.entity.JppaConfig;

@Repository
public interface JppaConfigRepository extends JpaRepository<JppaConfig, String> {

	List<JppaConfig> findAllByAbilitato(Boolean abilitato);

	Optional<JppaConfig> findByCodDominio(String codDominio);
}
