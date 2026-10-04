package com.majd.reachflow.integration;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.majd.reachflow.dto.CampaignStartRequestDTO;
import com.majd.reachflow.dto.ClientCategoryDTO;
import com.majd.reachflow.dto.ClientDTO;
import com.majd.reachflow.dto.LeadDTO;
import com.majd.reachflow.entity.Campaign;
import com.majd.reachflow.entity.Category;
import com.majd.reachflow.entity.enums.CampaignSendStatus;
import com.majd.reachflow.entity.enums.CampaignStatus;
import com.majd.reachflow.repository.CampaignRepository;
import com.majd.reachflow.repository.CampaignSendRepository;
import com.majd.reachflow.repository.CategoryRepository;
import com.majd.reachflow.service.CampaignExecutionService;
import com.majd.reachflow.service.ClientService;
import com.majd.reachflow.service.LeadService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a real campaign against an in-process SMTP server. Needs MySQL, so it only runs when
 * DB_HOST is set (CI sets it). These are the behaviours that used to be broken: start returning
 * immediately, statuses saved as each email goes out, Stop being honoured, resume not re-sending,
 * and the campaign leaving RUNNING when it ends.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class CampaignSendingIntegrationTest {

    private static final int SMTP_PORT = 3925;

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(new ServerSetup(SMTP_PORT, "127.0.0.1", "smtp"));

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("mail.smtp.host", () -> "127.0.0.1");
        registry.add("mail.smtp.port", () -> SMTP_PORT);
        registry.add("mail.smtp.secure", () -> "false");
    }

    @Autowired CategoryRepository categoryRepository;
    @Autowired ClientService clientService;
    @Autowired LeadService leadService;
    @Autowired CampaignRepository campaignRepository;
    @Autowired CampaignSendRepository campaignSendRepository;
    @Autowired CampaignExecutionService campaignExecutionService;

    private Long campaignId;
    private final Set<String> recipients = new HashSet<>();

    @BeforeEach
    void createClientLeadsAndCampaign() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        // A category of its own: campaigns email every lead of the client's categories, so sharing a
        // seeded one would also pick up leads left in the database by earlier runs.
        Category category = categoryRepository.save(Category.builder().name("IT category " + run).build());

        ClientDTO client = clientService.createClient(ClientDTO.builder()
                .name("IT client " + run)
                .email("sender-" + run + "@reachflow.test")
                .appPassword("not-used-by-the-test-smtp")
                .categories(List.of(ClientCategoryDTO.builder().categoryId(category.getId()).build()))
                .build());
        clientService.updateClientCategoryDocument(client.getId(), category.getId(),
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4".getBytes()));

        for (int i = 0; i < 6; i++) {
            String email = "lead" + i + "-" + run + "@firma-" + i + ".example";
            recipients.add(email);
            leadService.createOrSkipLead(LeadDTO.builder()
                    .institutionName("Firma " + i + " " + run)
                    .city("Berlin")
                    .email(email).primaryEmail(email).emails(List.of(email))
                    .source("integration test")
                    .categoryIds(List.of(category.getId()))
                    .build());
        }
        campaignId = campaignRepository.findAllByClientId(client.getId()).get(0).getId();
    }

    @Test
    void stopThenResumeSendsEveryRecipientExactlyOnce() throws Exception {
        long started = System.nanoTime();
        campaignExecutionService.startCampaign(campaignId, CampaignStartRequestDTO.builder()
                .subject("Hallo {institutionName}").body("Guten Tag").delaySeconds(1).htmlBody(false).build());
        assertTrue((System.nanoTime() - started) / 1_000_000 < 800,
                "start must return immediately, not after the whole send loop");

        // Statuses are saved as each email goes out, not at the end.
        await(() -> count(CampaignSendStatus.SENT) >= 2, 15_000);
        assertEquals(CampaignStatus.RUNNING, status());

        campaignExecutionService.stopCampaign(campaignId);
        await(() -> status() == CampaignStatus.DRAFT, 10_000);
        Thread.sleep(2_500); // a run that ignored the stop would send one more by now

        long sentAtStop = count(CampaignSendStatus.SENT);
        assertTrue(sentAtStop >= 2 && sentAtStop < recipients.size(), "stopped part-way, sent " + sentAtStop);
        assertEquals(sentAtStop, smtp.getReceivedMessages().length, "every SENT row has a delivered email");
        assertEquals(CampaignStatus.DRAFT, status(), "a stopped campaign is idle, not RUNNING");

        campaignExecutionService.startCampaign(campaignId, CampaignStartRequestDTO.builder()
                .subject("Hallo {institutionName}").body("Guten Tag").delaySeconds(0).htmlBody(false).build());
        await(() -> status() == CampaignStatus.COMPLETED, 20_000);

        MimeMessage[] delivered = smtp.getReceivedMessages();
        assertEquals(recipients.size(), delivered.length, "no recipient is emailed twice after the resume");
        Set<String> seen = new HashSet<>();
        for (MimeMessage message : delivered) {
            seen.add(message.getAllRecipients()[0].toString());
            assertTrue(GreenMailUtil.getBody(message).contains("Guten Tag"));
        }
        assertEquals(recipients, seen);
    }

    private CampaignStatus status() {
        return campaignRepository.findById(campaignId).map(Campaign::getStatus).orElseThrow();
    }

    private long count(CampaignSendStatus status) {
        return campaignSendRepository.countByCampaignIdAndStatus(campaignId, status);
    }

    private static void await(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within " + timeoutMillis + " ms");
            }
            Thread.sleep(150);
        }
    }
}
