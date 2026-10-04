package com.majd.reachflow.config;

import com.majd.reachflow.dto.ClientCategoryDTO;
import com.majd.reachflow.dto.ClientDTO;
import com.majd.reachflow.dto.LeadDTO;
import com.majd.reachflow.entity.Category;
import com.majd.reachflow.repository.CategoryRepository;
import com.majd.reachflow.repository.ClientRepository;
import com.majd.reachflow.service.ClientService;
import com.majd.reachflow.service.LeadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional demo data ({@code demo.seed=true}, set by docker-compose.demo.yml): one client with an
 * attachment per category and a spread of leads, so a fresh install opens onto a populated app
 * and a campaign can be sent straight away. Everything uses reserved example domains, so
 * nothing here can reach a real person. Idempotent: it does nothing if the client exists.
 */
@Component
@Order(2) // after CategorySeeder (1), which creates the categories used below
@ConditionalOnProperty(name = "demo.seed", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    static final String CLIENT_EMAIL = "recruiting@reachflow.test";

    private final ClientRepository clientRepository;
    private final CategoryRepository categoryRepository;
    private final ClientService clientService;
    private final LeadService leadService;

    private record Place(String city, double lat, double lon) { }

    private static final List<Place> PLACES = List.of(
            new Place("Berlin", 52.5200, 13.4050), new Place("Hamburg", 53.5511, 9.9937),
            new Place("München", 48.1351, 11.5820), new Place("Köln", 50.9375, 6.9603),
            new Place("Leipzig", 51.3397, 12.3731));

    private static final String[] SURNAMES = {"Schneider", "Fischer", "Weber", "Meyer", "Wagner"};

    @Override
    public void run(String... args) throws IOException {
        if (clientRepository.findAll().stream().anyMatch(c -> CLIENT_EMAIL.equals(c.getEmail()))) {
            log.info("Demo data already present, skipping");
            return;
        }

        List<Category> categories = new ArrayList<>();
        for (String name : List.of("Ausbildung", "IT Services", "Hospitality")) {
            categoryRepository.findByNameIgnoreCaseAndActiveTrue(name).ifPresent(categories::add);
        }
        if (categories.isEmpty()) {
            log.warn("Demo seed skipped: base categories are missing");
            return;
        }

        ClientDTO client = clientService.createClient(ClientDTO.builder()
                .name("Demo Recruiting GmbH")
                .email(CLIENT_EMAIL)
                .appPassword("demo-app-password")
                .phone("+49 30 1234567")
                .categories(categories.stream()
                        .map(c -> ClientCategoryDTO.builder().categoryId(c.getId()).build())
                        .toList())
                .build());
        for (Category category : categories) {
            clientService.updateClientCategoryDocument(client.getId(), category.getId(),
                    new InMemoryFile("Bewerbung-" + category.getName().replace(' ', '-') + ".pdf",
                            "application/pdf", minimalPdf()));
        }

        int leads = 0;
        String[][] kinds = {
                {"Metallbau", "Elektrotechnik", "Autohaus", "Logistik", "Sanitär"},
                {"Softwarehaus", "IT-Systemhaus", "Webagentur", "Datenzentrum", "Cloud Services"},
                {"Hotel", "Restaurant", "Café", "Catering", "Pension"}};
        for (int c = 0; c < categories.size(); c++) {
            for (int i = 0; i < 5; i++) {
                Place place = PLACES.get((c * 2 + i) % PLACES.size());
                String name = SURNAMES[i] + " " + kinds[c % kinds.length][i];
                String domain = (SURNAMES[i] + "-" + kinds[c % kinds.length][i]).toLowerCase()
                        .replace("ü", "ue").replace("ä", "ae").replace("ö", "oe").replace(' ', '-')
                        + "-" + place.city().toLowerCase().replace("ü", "ue").replace("ö", "oe") + ".example";
                List<String> emails = new ArrayList<>(List.of("kontakt@" + domain, "bewerbung@" + domain));
                if (leads == 3) {
                    emails.add("logo@2x-demo.png"); // a scraper artifact, for the email-audit page
                }
                leadService.createOrSkipLead(LeadDTO.builder()
                        .institutionName(name + (i % 2 == 0 ? " GmbH" : " KG"))
                        .city(place.city())
                        .address("Hauptstraße " + (10 + leads) + ", " + place.city())
                        .phone("+49 " + (30 + i) + " " + (1000000 + leads * 7919))
                        .website("https://www." + domain)
                        .latitude(place.lat() + (i - 2) * 0.01)
                        .longitude(place.lon() + (i - 2) * 0.01)
                        .email(emails.get(0))
                        .primaryEmail(emails.get(0))
                        .emails(emails)
                        .source("Demo seed")
                        .categoryIds(List.of(categories.get(c).getId()))
                        .build());
                leads++;
            }
        }
        log.info("Demo data created: 1 client, {} categories with attachments, {} leads", categories.size(), leads);
    }

    private static byte[] minimalPdf() {
        String pdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/MediaBox[0 0 200 200]/Parent 2 0 R>>endobj\n"
                + "trailer<</Root 1 0 R>>\n%%EOF";
        return pdf.getBytes(StandardCharsets.US_ASCII);
    }

    /** Spring's own in-memory MultipartFile lives in the test jar, so this is a tiny stand-in. */
    private record InMemoryFile(String name, String contentType, byte[] content) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), content); }
        @Override public void transferTo(Path dest) throws IOException { Files.write(dest, content); }
    }
}
