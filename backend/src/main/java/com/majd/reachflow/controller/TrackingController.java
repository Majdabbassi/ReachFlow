package com.majd.reachflow.controller;

import com.majd.reachflow.repository.CampaignSendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Base64;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@Slf4j
public class TrackingController {

    private final CampaignSendRepository campaignSendRepository;

    // 1x1 transparent GIF
    private static final byte[] PIXEL = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    @GetMapping(value = "/open/{token}", produces = MediaType.IMAGE_GIF_VALUE)
    @Transactional
    public ResponseEntity<byte[]> trackOpen(@PathVariable String token) {
        log.info("Received tracking request for token: {}", token);

        campaignSendRepository.findByTrackingToken(token).ifPresent(send -> {
            if (send.getOpenedAt() == null) {
                send.setOpenedAt(LocalDateTime.now());
                campaignSendRepository.save(send);
                log.info("Campaign send {} marked as OPENED", send.getId());
            }
        });

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_GIF)
                .body(PIXEL);
    }
}
