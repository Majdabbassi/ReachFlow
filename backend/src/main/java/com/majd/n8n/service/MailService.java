package com.majd.n8n.service;

import com.majd.n8n.entity.CampaignSend;
import com.majd.n8n.entity.Campaign;
import com.majd.n8n.entity.Client;
import com.majd.n8n.entity.ClientCategoryDocument;
import com.majd.n8n.entity.Lead;
import com.majd.n8n.entity.LeadCategory;
import com.majd.n8n.entity.LeadEmail;
import com.majd.n8n.entity.enums.CampaignSendStatus;
import com.majd.n8n.entity.enums.CampaignStatus;
import com.majd.n8n.repository.CampaignRepository;
import com.majd.n8n.repository.CampaignSendRepository;
import com.majd.n8n.repository.ClientCategoryDocumentRepository;
import com.majd.n8n.repository.ClientCategoryRepository;
import com.majd.n8n.repository.LeadCategoryRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {
    private final CampaignRepository campaignRepository;
    private final CampaignSendRepository campaignSendRepository;
    private final LeadCategoryRepository leadCategoryRepository;
    private final ClientCategoryRepository clientCategoryRepository;
    private final ClientCategoryDocumentRepository clientCategoryDocumentRepository;

    public void sendEmails(Long campaignId, Client client, String subject, String body, boolean htmlBody, List<CampaignSend> sends, int delaySeconds) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(465);
        mailSender.setUsername(client.getEmail());
        mailSender.setPassword(client.getAppPassword());
        mailSender.setProtocol("smtps");
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");

        for (int i = 0; i < sends.size(); i++) {
            Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
            if (campaign == null) {
                log.warn("Campaign {} no longer exists while sending emails", campaignId);
                break;
            }

            if (campaign.getStatus() == CampaignStatus.STOP_REQUESTED) {
                campaign.setStatus(CampaignStatus.DRAFT);
                campaignRepository.save(campaign);
                log.info("Stop requested for campaign {}, halting send loop", campaignId);
                break;
            }

            CampaignSend send = sends.get(i);
            try {
                LeadEmail leadEmail = send.getLeadEmail();
                String recipientEmail = leadEmail == null ? null : leadEmail.getEmail();
                if (!isValidRecipientEmail(recipientEmail)) {
                    log.warn("Skipping invalid recipient email {}", recipientEmail);
                    send.setStatus(CampaignSendStatus.FAILED);
                    continue;
                }
                String personalizedSubject = applyTemplate(subject, leadEmail);
                String personalizedBody = applyTemplate(body, leadEmail);

                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(client.getEmail());
                helper.setTo(recipientEmail);
                helper.setSubject(personalizedSubject);
                helper.setText(personalizedBody, htmlBody);
                addCategoryAttachment(helper, client, leadEmail);
                mailSender.send(message);
                send.setStatus(CampaignSendStatus.SENT);
                send.setSentAt(LocalDateTime.now());
            } catch (MessagingException e) {
                log.error("Failed to send email to {}: {}", send.getLeadEmail().getEmail(), e.getMessage());
                send.setStatus(isBounceError(e) ? CampaignSendStatus.BOUNCED : CampaignSendStatus.FAILED);
            } catch (RuntimeException e) {
                log.error("Failed to send email to {}: {}", send.getLeadEmail().getEmail(), e.getMessage());
                send.setStatus(CampaignSendStatus.FAILED);
            }

            if (delaySeconds > 0 && i < sends.size() - 1) {
                try {
                    Thread.sleep(delaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Email sending delay interrupted, continuing without further wait");
                }
            }
        }
        campaignSendRepository.saveAll(sends);
    }

    private boolean isValidRecipientEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isBounceError(Throwable throwable) {
        String fullMessage = buildThrowableMessage(throwable).toLowerCase();
        List<String> bounceTokens = List.of(
                "550", "551", "552", "553", "554",
                "user unknown", "no such user", "address rejected",
                "invalid address", "does not exist"
        );

        for (String token : bounceTokens) {
            if (fullMessage.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String buildThrowableMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(' ').append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private String applyTemplate(String template, LeadEmail leadEmail) {
        String safeTemplate = template == null ? "" : template;
        Lead lead = leadEmail.getLead();
        String institution = lead.getInstitutionName() == null || lead.getInstitutionName().isBlank()
                ? leadEmail.getEmail()
                : lead.getInstitutionName();

        return safeTemplate
                .replace("{name}", institution)
                .replace("{institutionName}", institution)
                .replace("{city}", valueOrEmpty(lead.getCity()))
                .replace("{website}", valueOrEmpty(lead.getWebsite()))
                .replace("{email}", valueOrEmpty(leadEmail.getEmail()));
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void addCategoryAttachment(MimeMessageHelper helper, Client client, LeadEmail leadEmail) throws MessagingException {
        if (leadEmail == null || leadEmail.getLead() == null) {
            return;
        }

        List<LeadCategory> leadCategories = leadCategoryRepository.findByLeadId(leadEmail.getLead().getId());
        if (leadCategories.isEmpty()) {
            return;
        }

        Set<Long> clientCategoryIds = Set.copyOf(clientCategoryRepository.findCategoryIdsByClientId(client.getId()));
        if (clientCategoryIds.isEmpty()) {
            return;
        }

        for (LeadCategory leadCategory : leadCategories) {
            if (leadCategory.getCategory() == null || !leadCategory.getCategory().isActive()) {
                continue;
            }

            Long categoryId = leadCategory.getCategory().getId();
            if (!clientCategoryIds.contains(categoryId)) {
                continue;
            }

            ClientCategoryDocument document = clientCategoryDocumentRepository.findByClientIdAndCategoryId(client.getId(), categoryId)
                    .orElse(null);
            if (document == null || document.getDocument() == null || document.getDocument().length == 0) {
                return;
            }

            String fileName = document.getDocumentName();
            if (fileName == null || fileName.isBlank()) {
                fileName = "client-category-document";
            }

            String contentType = document.getDocumentContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            helper.addAttachment(fileName, new ByteArrayResource(document.getDocument()), contentType);
            return;
        }
    }
}
