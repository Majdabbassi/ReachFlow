package com.majd.n8n.service;

import com.majd.n8n.dto.GmailScanResultDTO;
import com.majd.n8n.entity.CampaignSend;
import com.majd.n8n.entity.Client;
import com.majd.n8n.entity.enums.CampaignSendStatus;
import com.majd.n8n.repository.CampaignSendRepository;
import com.majd.n8n.repository.ClientRepository;
import com.majd.n8n.repository.LeadEmailRepository;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GmailScannerService {

    private final ClientRepository clientRepository;
    private final LeadEmailRepository leadEmailRepository;
    private final CampaignSendRepository campaignSendRepository;

    @Transactional
    public GmailScanResultDTO scanAndMarkSentEmails(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + clientId));

        Set<String> scannedAddresses = new LinkedHashSet<>();
        List<CampaignSend> sendsToUpdate = new ArrayList<>();

        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", "imap.gmail.com");
        properties.put("mail.imaps.port", "993");
        properties.put("mail.imaps.ssl.enable", "true");

        Session session = Session.getInstance(properties);
        Store store = null;
        Folder sentFolder = null;

        try {
            store = session.getStore("imaps");
            store.connect("imap.gmail.com", 993, client.getEmail(), client.getAppPassword());
            sentFolder = store.getFolder("[Gmail]/Sent Mail");
            if (!sentFolder.exists()) {
                throw new RuntimeException("Sent Mail folder not found in Gmail account");
            }

            sentFolder.open(Folder.READ_ONLY);
            for (Message message : sentFolder.getMessages()) {
                collectRecipients(message.getRecipients(Message.RecipientType.TO), scannedAddresses);
                collectRecipients(message.getRecipients(Message.RecipientType.CC), scannedAddresses);
            }

            LocalDateTime now = LocalDateTime.now();
            for (String address : scannedAddresses) {
                leadEmailRepository.findByEmail(address).ifPresent(leadEmail ->
                        sendsToUpdate.addAll(campaignSendRepository.findByLeadEmailId(leadEmail.getId()).stream()
                                .filter(send -> send.getCampaign() != null
                                        && send.getCampaign().getClient() != null
                                        && clientId.equals(send.getCampaign().getClient().getId())
                                        && send.getStatus() == CampaignSendStatus.PENDING)
                                .peek(send -> {
                                    send.setStatus(CampaignSendStatus.SENT);
                                    send.setSentAt(now);
                                })
                                .toList())
                );
            }

            if (!sendsToUpdate.isEmpty()) {
                campaignSendRepository.saveAll(sendsToUpdate);
            }

            return GmailScanResultDTO.builder()
                    .scannedCount(scannedAddresses.size())
                    .markedAsSentCount(sendsToUpdate.size())
                    .build();
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to connect to Gmail: " + e.getMessage(), e);
        } finally {
            try {
                if (sentFolder != null && sentFolder.isOpen()) {
                    sentFolder.close(false);
                }
            } catch (MessagingException ignored) {
            }
            try {
                if (store != null && store.isConnected()) {
                    store.close();
                }
            } catch (MessagingException ignored) {
            }
        }
    }

    private void collectRecipients(Address[] addresses, Set<String> output) {
        if (addresses == null) {
            return;
        }

        for (Address address : addresses) {
            if (address instanceof InternetAddress internetAddress) {
                String value = internetAddress.getAddress();
                if (value != null && !value.trim().isEmpty()) {
                    output.add(value.trim().toLowerCase());
                }
            }
        }
    }
}