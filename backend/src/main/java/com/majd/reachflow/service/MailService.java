package com.majd.reachflow.service;

import com.majd.reachflow.entity.CampaignSend;
import com.majd.reachflow.entity.Campaign;
import com.majd.reachflow.entity.Client;
import com.majd.reachflow.entity.ClientCategoryDocument;
import com.majd.reachflow.entity.Lead;
import com.majd.reachflow.entity.LeadCategory;
import com.majd.reachflow.entity.LeadEmail;
import com.majd.reachflow.entity.enums.CampaignSendStatus;
import com.majd.reachflow.entity.enums.CampaignStatus;
import com.majd.reachflow.repository.CampaignRepository;
import com.majd.reachflow.repository.CampaignSendRepository;
import com.majd.reachflow.repository.ClientCategoryDocumentRepository;
import com.majd.reachflow.repository.ClientCategoryRepository;
import com.majd.reachflow.repository.LeadCategoryRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

    // Gmail by default. Demo mode points these at a local fake SMTP server (MailHog).
    @org.springframework.beans.factory.annotation.Value("${mail.smtp.host:smtp.gmail.com}")
    private String smtpHost;
    @org.springframework.beans.factory.annotation.Value("${mail.smtp.port:465}")
    private int smtpPort;
    @org.springframework.beans.factory.annotation.Value("${mail.smtp.secure:true}")
    private boolean smtpSecure;

    /**
     * Sends the given campaign sends one by one, pausing {@code delaySeconds} between emails.
     *
     * <p>Deliberately not transactional as a whole: every email runs in its own short
     * transaction. A single transaction around the entire loop (which can last hours) meant
     * the statuses were only written at the very end, so a crash re-sent everyone, the UI
     * showed nothing sent, a stop request was never seen (stale snapshot) and the campaign
     * could never be marked finished.
     *
     * @return true if every send was processed, false if the run was cut short (stop requested,
     *         campaign deleted)
     */
    public boolean sendEmails(Long campaignId, List<Long> sendIds, String subject, String body,
                           boolean htmlBody, int delaySeconds) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        JavaMailSenderImpl mailSender = tx.execute(status -> buildMailSender(campaignId));
        if (mailSender == null) {
            log.warn("Campaign {} no longer exists, nothing to send", campaignId);
            return false;
        }

        for (int i = 0; i < sendIds.size(); i++) {
            Long sendId = sendIds.get(i);
            Boolean keepGoing = tx.execute(status -> sendOne(campaignId, sendId, mailSender, subject, body, htmlBody));
            if (!Boolean.TRUE.equals(keepGoing)) {
                return false;
            }

            if (delaySeconds > 0 && i < sendIds.size() - 1) {
                try {
                    Thread.sleep(delaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Email sending delay interrupted, continuing without further wait");
                }
            }
        }
        return true;
    }

    private JavaMailSenderImpl buildMailSender(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            return null;
        }
        Client client = campaign.getClient();

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtpHost);
        mailSender.setPort(smtpPort);
        Properties props = mailSender.getJavaMailProperties();
        if (smtpSecure) {
            mailSender.setUsername(client.getEmail());
            mailSender.setPassword(client.getAppPassword());
            mailSender.setProtocol("smtps");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", smtpHost);
        } else {
            // Plain SMTP without credentials: only for a local test inbox such as MailHog.
            mailSender.setProtocol("smtp");
            props.put("mail.smtp.auth", "false");
        }
        return mailSender;
    }

    /**
     * Handles one send inside its own transaction.
     *
     * @return false when the whole run should stop (campaign deleted or a stop was requested)
     */
    private boolean sendOne(Long campaignId, Long sendId, JavaMailSenderImpl mailSender,
                            String subject, String body, boolean htmlBody) {
        // A fresh transaction per email also means a fresh read: a stop requested from the UI
        // while the loop is running is seen on the very next iteration.
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            log.warn("Campaign {} no longer exists while sending emails", campaignId);
            return false;
        }
        if (campaign.getStatus() == CampaignStatus.STOP_REQUESTED) {
            campaign.setStatus(CampaignStatus.DRAFT);
            campaignRepository.save(campaign);
            log.info("Stop requested for campaign {}, halting send loop", campaignId);
            return false;
        }

        CampaignSend send = campaignSendRepository.findById(sendId).orElse(null);
        if (send == null || !campaignId.equals(send.getCampaign().getId())) {
            log.warn("Send {} does not belong to campaign {}, skipping", sendId, campaignId);
            return true;
        }
        Client client = campaign.getClient();

        try {
            LeadEmail leadEmail = send.getLeadEmail();
            String recipientEmail = leadEmail == null ? null : leadEmail.getEmail();
            if (!isValidRecipientEmail(recipientEmail)) {
                log.warn("Skipping invalid recipient email {}", recipientEmail);
                send.setStatus(CampaignSendStatus.FAILED);
                campaignSendRepository.save(send);
                return true;
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
        campaignSendRepository.save(send);
        return true;
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
