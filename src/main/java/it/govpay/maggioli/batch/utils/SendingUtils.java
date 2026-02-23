package it.govpay.maggioli.batch.utils;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.maggioli.batch.dto.Contabilita;
import it.govpay.maggioli.batch.dto.QuotaContabilita;
import it.govpay.maggioli.batch.entity.SingoloVersamento;
import it.govpay.maggioli.client.model.DatoAccertamentoDto;

public class SendingUtils {
	private SendingUtils() {
		// 
	}

	private static DatoAccertamentoDto buildDatoAccertamentoFromQuota(QuotaContabilita quota, String descrVersamento) {
        DatoAccertamentoDto datiAccertamento = new DatoAccertamentoDto();
        datiAccertamento.setAnnoAccertamento(quota.getAnnoEsercizio() + "");
        datiAccertamento.setCodiceAccertamento(quota.getCapitolo());
        datiAccertamento.setDescrizioneAccertamento(descrVersamento);
		datiAccertamento.setImportoAccertamento(quota.getImporto());
		return datiAccertamento;
	}

	private static List<DatoAccertamentoDto> contabilitaConverter(String contabilita, String descrVersamento) {
		if (contabilita == null) {
			return List.of();
		}
		try {
			ObjectMapper mapper = new ObjectMapper();
	        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Rome"));
	        sdf.setLenient(false);
	        mapper.setDateFormat(sdf);
	        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
	        mapper.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);

			Contabilita contabilitaDto = mapper.readValue(contabilita, Contabilita.class);
			if(contabilitaDto.getQuote() != null && !contabilitaDto.getQuote().isEmpty()) {
				return contabilitaDto.getQuote().stream().map(quota -> buildDatoAccertamentoFromQuota(quota, descrVersamento)).toList();
			}
	    	return List.of();
		} catch (JsonProcessingException excp) {
			throw new RuntimeException("Parsing error", excp);
		}
	}

	public static List<DatoAccertamentoDto> buildDatiAccertamento(Set<SingoloVersamento> singoliVersamenti) {
		if (singoliVersamenti != null) {
			return singoliVersamenti.stream().map(sv -> contabilitaConverter(sv.getContabilita(), sv.getDescrizione()))
											 .flatMap(Collection::stream)
											 .toList();
		}
		return List.of();
	}
}
