package com.majd.n8n.integration;

import com.majd.n8n.dto.N8nPayloadDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class N8nService {

    private final RestTemplate restTemplate;

    public N8nService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void sendToN8n(String webhookUrl, N8nPayloadDTO payload) {
        log.info("Sending payload to n8n webhook: {}", webhookUrl);
        restTemplate.postForEntity(webhookUrl, payload, String.class);
        log.info("Successfully sent payload to n8n");
    }
}
