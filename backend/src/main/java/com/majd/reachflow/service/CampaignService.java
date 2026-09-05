package com.majd.reachflow.service;

import com.majd.reachflow.dto.CampaignDTO;
import com.majd.reachflow.dto.CampaignScheduleRequestDTO;
import com.majd.reachflow.dto.CampaignSendDTO;
import com.majd.reachflow.dto.CampaignStartRequestDTO;
import com.majd.reachflow.dto.CampaignStatsDTO;
import com.majd.reachflow.dto.SelectiveSendRequestDTO;
import com.majd.reachflow.entity.Campaign;
import com.majd.reachflow.entity.CampaignSend;
import com.majd.reachflow.entity.CampaignStartRequest;
import com.majd.reachflow.entity.Client;
import com.majd.reachflow.entity.enums.CampaignSendStatus;
import com.majd.reachflow.entity.enums.CampaignStatus;
import com.majd.reachflow.exception.BusinessException;
import com.majd.reachflow.mapper.CampaignMapper;
import com.majd.reachflow.repository.CampaignRepository;
import com.majd.reachflow.repository.CampaignSendRepository;
import com.majd.reachflow.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignSendRepository campaignSendRepository;
    private final ClientRepository clientRepository;
    private final CampaignMapper campaignMapper;
    private final CampaignExecutionService campaignExecutionService;

    @Transactional(readOnly = true)
    public List<CampaignDTO> getAllCampaigns() {
        return campaignRepository.findAll().stream()
                .map(campaignMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CampaignDTO getCampaignById(Long id) {
        campaignExecutionService.syncCampaignSendsWithAllLeads(id);
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + id, HttpStatus.NOT_FOUND));
        return campaignMapper.toDTO(campaign);
    }

    @Transactional
    public CampaignDTO updateCampaign(Long id, CampaignDTO campaignDTO) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + id, HttpStatus.NOT_FOUND));
        campaign.setName(campaignDTO.getName());
        if (campaignDTO.getStatus() != null) {
            campaign.setStatus(campaignDTO.getStatus());
        }
        return campaignMapper.toDTO(campaignRepository.save(campaign));
    }

    @Transactional
    public void generateCampaignSends(Long id) {
        campaignExecutionService.syncCampaignSendsWithAllLeads(id);
    }

    @Transactional
    public void startCampaign(Long id, CampaignStartRequestDTO request) {
        campaignExecutionService.startCampaign(id, request);
    }

    @Transactional
    public void scheduleCampaign(Long campaignId, CampaignScheduleRequestDTO request) {
        if (request == null || request.getScheduledAt() == null) {
            throw new BusinessException("scheduledAt is required", HttpStatus.BAD_REQUEST);
        }

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + campaignId, HttpStatus.NOT_FOUND));

        if (campaign.getStatus() == CampaignStatus.RUNNING) {
            throw new BusinessException("Cannot schedule a running campaign", HttpStatus.CONFLICT);
        }

        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setScheduledAt(request.getScheduledAt());
        campaign.setStartRequest(CampaignStartRequest.builder()
                .subject(request.getSubject())
                .body(request.getBody())
                .delaySeconds(request.getDelaySeconds())
                .htmlBody(Boolean.TRUE.equals(request.getHtmlBody()))
                .build());
        campaignRepository.save(campaign);
    }

    @Transactional(readOnly = true)
    public List<Campaign> getSchedulableCampaigns(LocalDateTime now) {
        return campaignRepository.findByStatusAndScheduledAtIsNotNullAndScheduledAtLessThanEqual(CampaignStatus.DRAFT, now);
    }

    @Transactional
    public void stopCampaign(Long id) {
        campaignExecutionService.stopCampaign(id);
    }

    @Transactional
    public void sendSelected(Long campaignId, SelectiveSendRequestDTO request) {
        campaignExecutionService.sendSelected(campaignId, request);
    }

    @Transactional
    public CampaignStatsDTO getCampaignStats(Long id) {
        campaignExecutionService.syncCampaignSendsWithAllLeads(id);
        long total = campaignSendRepository.countByCampaignId(id);
        long sent = campaignSendRepository.countByCampaignIdAndStatus(id, CampaignSendStatus.SENT);
        long replied = campaignSendRepository.countByCampaignIdAndStatus(id, CampaignSendStatus.REPLIED);
        long pending = campaignSendRepository.countByCampaignIdAndStatus(id, CampaignSendStatus.PENDING);
        long failed = campaignSendRepository.countByCampaignIdAndStatus(id, CampaignSendStatus.FAILED);
        long bounced = campaignSendRepository.countByCampaignIdAndStatus(id, CampaignSendStatus.BOUNCED);
        return CampaignStatsDTO.builder()
                .total(total)
                .sent(sent)
                .replied(replied)
                .pending(pending)
                .failed(failed)
                .bounced(bounced)
                .build();
    }

    @Transactional
    public void updateSendStatus(Long sendId, String status) {
        CampaignSend campaignSend = campaignSendRepository.findById(sendId)
                .orElseThrow(() -> new BusinessException("CampaignSend not found with id: " + sendId, HttpStatus.NOT_FOUND));
        try {
            campaignSend.setStatus(CampaignSendStatus.valueOf(status.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid campaign send status: " + status, HttpStatus.BAD_REQUEST);
        }
        campaignSend.setSentAt(LocalDateTime.now());
        campaignSendRepository.save(campaignSend);
        campaignExecutionService.reconcileCampaignStatuses();
        campaignExecutionService.checkCampaignCompletion(campaignSend.getCampaign().getId());
    }

    @Transactional
    public Page<CampaignSendDTO> getCampaignSends(Long campaignId, String status, Pageable pageable) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new BusinessException("Campaign not found with id: " + campaignId, HttpStatus.NOT_FOUND);
        }

        campaignExecutionService.syncCampaignSendsWithAllLeads(campaignId);

        Page<CampaignSend> sendPage;
        if (status == null || status.isBlank()) {
            sendPage = campaignSendRepository.findByCampaignId(campaignId, pageable);
        } else {
            CampaignSendStatus sendStatus;
            try {
                sendStatus = CampaignSendStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("Invalid campaign send status: " + status, HttpStatus.BAD_REQUEST);
            }
            sendPage = campaignSendRepository.findByCampaignIdAndStatus(campaignId, sendStatus, pageable);
        }

        return sendPage.map(send -> CampaignSendDTO.builder()
                .id(send.getId())
                .campaignId(send.getCampaign().getId())
                .leadEmailId(send.getLeadEmail().getId())
                .email(send.getLeadEmail().getEmail())
                .leadId(send.getLeadEmail().getLead().getId())
                .leadInstitutionName(send.getLeadEmail().getLead().getInstitutionName())
                .leadCity(send.getLeadEmail().getLead().getCity())
                .status(send.getStatus())
                .sentAt(send.getSentAt())
                .repliedAt(send.getRepliedAt())
                .build());
    }

    @Transactional
    public void ensureCampaignForEachClient() {
        List<Client> clients = clientRepository.findAll();
        for (Client client : clients) {
            if (!campaignRepository.existsByClientId(client.getId())) {
                Campaign campaign = Campaign.builder()
                        .name(client.getName() + " Campaign")
                        .status(CampaignStatus.DRAFT)
                        .client(client)
                        .build();
                campaignRepository.save(campaign);
            }
        }
    }
}
