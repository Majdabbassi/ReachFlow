package com.majd.n8n.service;

import com.majd.n8n.dto.GmailScanResultDTO;
import com.majd.n8n.entity.CampaignSend;
import com.majd.n8n.entity.Client;
import com.majd.n8n.entity.enums.CampaignSendStatus;
import com.majd.n8n.exception.BusinessException;
import com.majd.n8n.repository.CampaignSendRepository;
import com.majd.n8n.repository.ClientRepository;
import com.majd.n8n.repository.LeadEmailRepository;
import com.majd.n8n.entity.LeadEmail;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
                .orElseThrow(() -> new BusinessException("Client not found with id: " + clientId, HttpStatus.NOT_FOUND));

        Set<String> scannedAddresses = new LinkedHashSet<>();
        List<CampaignSend> sendsToUpdate = new ArrayList<>();

        Store store = null;
        Folder folder = null;

        try {
            store = createImapStore(client);
            folder = store.getFolder("[Gmail]/Sent Mail");
            if (!folder.exists()) {
                throw new BusinessException("Sent Mail folder not found in Gmail account", HttpStatus.NOT_FOUND);
            }

            folder.open(Folder.READ_ONLY);
            Date cutoffDate = new Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
            SearchTerm searchTerm = new ReceivedDateTerm(ComparisonTerm.GE, cutoffDate);
            for (Message message : folder.search(searchTerm)) {
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
            throw new BusinessException("Failed to connect to Gmail: " + e.getMessage(), HttpStatus.BAD_REQUEST, e);
        } finally {
            closeQuietly(folder, store);
        }
    }

    @Transactional
    public GmailScanResultDTO scanAndMarkRepliedEmails(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new BusinessException("Client not found with id: " + clientId, HttpStatus.NOT_FOUND));

        Set<String> scannedAddresses = new LinkedHashSet<>();
        List<CampaignSend> sendsToUpdate = new ArrayList<>();

        Store store = null;
        Folder folder = null;

        try {
            store = createImapStore(client);
            folder = store.getFolder("INBOX");
            if (!folder.exists()) {
                throw new BusinessException("INBOX folder not found in Gmail account", HttpStatus.NOT_FOUND);
            }

            folder.open(Folder.READ_ONLY);
            Date cutoffDate = new Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
            SearchTerm searchTerm = new ReceivedDateTerm(ComparisonTerm.GE, cutoffDate);

            for (Message message : folder.search(searchTerm)) {
                collectRecipients(message.getFrom(), scannedAddresses);
            }

            Map<Long, LeadEmail> leadEmailById = new HashMap<>();
            if (!scannedAddresses.isEmpty()) {
                for (LeadEmail leadEmail : leadEmailRepository.findAllByNormalizedEmailIn(scannedAddresses)) {
                    leadEmailById.put(leadEmail.getId(), leadEmail);
                }
            }

            LocalDateTime now = LocalDateTime.now();
            for (LeadEmail leadEmail : leadEmailById.values()) {
                sendsToUpdate.addAll(campaignSendRepository.findByLeadEmailId(leadEmail.getId()).stream()
                        .filter(send -> send.getCampaign() != null
                                && send.getCampaign().getClient() != null
                                && clientId.equals(send.getCampaign().getClient().getId())
                                && send.getStatus() == CampaignSendStatus.SENT)
                        .peek(send -> {
                            send.setStatus(CampaignSendStatus.REPLIED);
                            send.setRepliedAt(now);
                        })
                        .toList());
            }

            if (!sendsToUpdate.isEmpty()) {
                campaignSendRepository.saveAll(sendsToUpdate);
            }

            return GmailScanResultDTO.builder()
                    .scannedCount(scannedAddresses.size())
                    .markedAsRepliedCount(sendsToUpdate.size())
                    .build();
        } catch (MessagingException e) {
            throw new BusinessException("Failed to connect to Gmail: " + e.getMessage(), HttpStatus.BAD_REQUEST, e);
        } finally {
            closeQuietly(folder, store);
        }
    }

    private Store createImapStore(Client client) throws MessagingException {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", "imap.gmail.com");
        properties.put("mail.imaps.port", "993");
        properties.put("mail.imaps.ssl.enable", "true");

        Session session = Session.getInstance(properties);
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", 993, client.getEmail(), client.getAppPassword());
        return store;
    }

    private void closeQuietly(Folder folder, Store store) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
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