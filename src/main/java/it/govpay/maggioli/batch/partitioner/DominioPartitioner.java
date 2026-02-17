package it.govpay.maggioli.batch.partitioner;

import it.govpay.maggioli.batch.entity.JppaConfig;
import it.govpay.maggioli.batch.repository.JppaConfigRepository;
import it.govpay.maggioli.batch.repository.JppaNotificheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Partitioner che divide il lavoro per cod_dominio.
 * Ogni partizione processa tutti i flussi di un singolo ente creditore.
 */
@Component
@Slf4j
public class DominioPartitioner implements Partitioner {

    private final JppaNotificheRepository jppaNotificheRepository;
    private final JppaConfigRepository jppaConfigRepository;

    public DominioPartitioner(JppaNotificheRepository jppaNotificheRepository, JppaConfigRepository jppaConfigRepository) {
        this.jppaNotificheRepository = jppaNotificheRepository;
        this.jppaConfigRepository = jppaConfigRepository;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // Recupera tutti i cod_dominio distinti presenti in JPPA_NOTIFICHE
        List<String> domini = jppaNotificheRepository.findDistinctCodDominio();

        log.info("Creazione partizioni: trovati {} domini in JPPA_NOTIFICHE", domini.size());

        Map<String, ExecutionContext> partitions = new HashMap<>();

        for (int i = 0; i < domini.size(); i++) {
            String codDominio = domini.get(i);

            Optional<JppaConfig> jppaConfigOpt = jppaConfigRepository.findByCodDominio(codDominio);
            if (jppaConfigOpt.isEmpty() || jppaConfigOpt.get().getConnettore() == null) {
                log.warn("Nessun connettore configurato per il dominio {}, partizione ignorata", codDominio);
                continue;
            }

            String codConnettore = jppaConfigOpt.get().getConnettore();

            ExecutionContext context = new ExecutionContext();
            context.putString("codDominio", codDominio);
            context.putString("codConnettore", codConnettore);
            context.putInt("partitionNumber", i + 1);
            context.putInt("totalPartitions", domini.size());

            // Nome partizione: partition-dominio
            String partitionName = "partition-" + codDominio;
            partitions.put(partitionName, context);

            log.debug("Creata partizione #{} per dominio: {} con connettore: {}", i + 1, codDominio, codConnettore);
        }

        log.info("Partizioni create: {} (gridSize richiesto: {})", partitions.size(), gridSize);
        return partitions;
    }
}
