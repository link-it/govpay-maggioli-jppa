package it.govpay.maggioli.batch.step3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import it.govpay.maggioli.batch.entity.RPT;
import it.govpay.maggioli.batch.service.NotificheApiService;
import it.govpay.maggioli.client.model.RispostaNotificaPagamentoDto;

/**
 * Processor to send notifica via Maggioli API
 */
@Component
@StepScope
@Slf4j
public class SendNotificationProcessor implements ItemProcessor<RPT, SendNotificationProcessor.NotificationCompleteData> {

    private final NotificheApiService notificheApiService;
    private final String codConnettore;

    public SendNotificationProcessor(
        NotificheApiService notificheApiService,
        @Value("#{stepExecutionContext['codConnettore']}") String codConnettore
    ) {
        this.notificheApiService = notificheApiService;
        this.codConnettore = codConnettore;
    }

    private String msgListAsString(List<String> msgList) {
    	if (msgList == null)
    		return null;
    	return String.join("\n",msgList);
    }

    @Override
    public NotificationCompleteData process(RPT rpt) throws Exception {
        log.info("Processing RPT: ec={}, iuv={}, idRicevuta={}, connettore={}", rpt.getCodDominio(), rpt.getIuv(), rpt.getCcp(), codConnettore);

        try {
            // Send notification
        	RispostaNotificaPagamentoDto clientResp = notificheApiService.notificaPagamento(codConnettore, rpt.getCodDominio(), rpt.getVersamento().getSingoliVersamenti(), rpt.getXmlRt());

            return NotificationCompleteData.builder()
                .codDominio(rpt.getCodDominio())
                .dataMsgRicevuta(rpt.getDataMsgRicevuta())
                .iuv(rpt.getIuv())
                .ccp(rpt.getCcp())
                .esito(clientResp.getEsito().name())
                .warnings(msgListAsString(clientResp.getWarningMessages()))
                .errors(msgListAsString(clientResp.getErrorMessages()))
                .build();

        } catch (RestClientException e) {
            log.error("Errore nell'elaborazione della notifica della ricevuta ec={}, iuv={}, idRicevuta={}: {}", rpt.getCodDominio(), rpt.getIuv(), rpt.getCcp(), e.getMessage());
            throw e;
        }
    }

    /**
     * DTO containing complete Maggioli JPPA data to build Notification
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class NotificationCompleteData {
        private String codDominio;
        private Instant dataMsgRicevuta;
        private String iuv;
        private String ccp;
        private String esito;
        private String warnings;
        private String errors;
    }

}
