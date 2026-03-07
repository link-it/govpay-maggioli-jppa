package it.govpay.maggioli.batch.service;

import org.springframework.stereotype.Service;

import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.mail.AbstractMailService;

@Service
public class MaggioliMailService extends AbstractMailService {

    public MaggioliMailService(ConfigurazioneService configurazioneService) {
        super(configurazioneService);
    }
}
