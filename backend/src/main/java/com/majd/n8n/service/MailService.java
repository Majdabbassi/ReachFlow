package com.majd.n8n.service;

import com.majd.n8n.entity.Client;
import com.majd.n8n.entity.CampaignSend;
import com.majd.n8n.entity.Lead;
import com.majd.n8n.entity.enums.CampaignSendStatus;
import com.majd.n8n.repository.CampaignSendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {
    private final CampaignSendRepository campaignSendRepository;

    public void sendEmails(Client client, String subject, String body, boolean htmlBody, List<CampaignSend> sends, int delaySeconds) {
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
            CampaignSend send = sends.get(i);
            try {
                Lead lead = send.getLead();
                String personalizedSubject = applyTemplate(subject, lead);
                String personalizedBody = applyTemplate(body, lead);

                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(client.getEmail());
                helper.setTo(lead.getEmail());
                helper.setSubject(personalizedSubject);
                helper.setText(personalizedBody, htmlBody);
                addClientAttachment(helper, client);
                mailSender.send(message);
                send.setStatus(CampaignSendStatus.SENT);
                send.setSentAt(LocalDateTime.now());
            } catch (MessagingException | RuntimeException e) {
                log.error("Failed to send email to {}: {}", send.getLead().getEmail(), e.getMessage());
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

    private String applyTemplate(String template, Lead lead) {
        String safeTemplate = template == null ? "" : template;
        String institution = lead.getInstitutionName() == null || lead.getInstitutionName().isBlank()
                ? lead.getEmail()
                : lead.getInstitutionName();

        return safeTemplate
                .replace("{name}", institution)
                .replace("{institutionName}", institution)
                .replace("{city}", valueOrEmpty(lead.getCity()))
                .replace("{website}", valueOrEmpty(lead.getWebsite()))
                .replace("{email}", valueOrEmpty(lead.getEmail()));
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void addClientAttachment(MimeMessageHelper helper, Client client) throws MessagingException {
        byte[] document = client.getDocument();
        if (document == null || document.length == 0) {
            return;
        }

        String fileName = client.getDocumentName();
        if (fileName == null || fileName.isBlank()) {
            fileName = "client-document";
        }

        String contentType = client.getDocumentContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        helper.addAttachment(fileName, new ByteArrayResource(document), contentType);
    }
}
