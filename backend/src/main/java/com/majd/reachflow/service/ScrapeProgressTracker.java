package com.majd.reachflow.service;

import com.majd.reachflow.dto.ScrapeProgressDTO;
import com.majd.reachflow.entity.enums.ScrapeStatus;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

@Service
@Slf4j
public class ScrapeProgressTracker {

    private static final long TTL_MINUTES = 60L;
    private final ConcurrentMap<String, ScrapeProgressDTO> jobs = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        log.info("ScrapeProgressTracker initialized (in-memory, TTL={} min)", TTL_MINUTES);
    }

    public String newJobId() {
        return UUID.randomUUID().toString();
    }

    public void registerJob(String jobId, String keywordsCsv, String citiesCsv) {
        jobs.put(jobId, ScrapeProgressDTO.builder()
                .jobId(jobId)
                .status(ScrapeStatus.PENDING)
                .message("Request queued. Waiting for scraper engine...")
                .keywords(keywordsCsv)
                .cities(citiesCsv)
                .startedAt(LocalDateTime.now())
                .build());
    }

    public void setRunning(String jobId, int expectedMax) {
        update(jobId, d -> {
            d.setStatus(ScrapeStatus.RUNNING);
            d.setMessage("Apify scraper is running against " + expectedMax + " targets...");
        });
    }

    public void setScraperReturned(String jobId, int rawResultsFound) {
        update(jobId, d -> {
            d.setStatus(ScrapeStatus.RUNNING);
            d.setLeadsFound(rawResultsFound);
            d.setMessage("Scraper returned " + rawResultsFound + " raw leads. Importing into DB...");
        });
    }

    public void setProgress(String jobId, int importedSoFar, int total) {
        update(jobId, d -> {
            d.setLeadsImported(importedSoFar);
            d.setMessage("Importing leads into ReachFlow... " + importedSoFar + " / " + total);
        });
    }

    public void setCompleted(String jobId, int imported) {
        LocalDateTime now = LocalDateTime.now();
        update(jobId, d -> {
            d.setStatus(ScrapeStatus.COMPLETED);
            d.setLeadsImported(imported);
            d.setMessage("Done. " + imported + " new or updated leads are available in the lead list.");
            d.setFinishedAt(now);
        });
    }

    public void setFailed(String jobId, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        update(jobId, d -> {
            d.setStatus(ScrapeStatus.FAILED);
            d.setErrorMessage(truncate(errorMessage, 500));
            d.setMessage("Scrape failed — check the debug logs or backend console.");
            d.setFinishedAt(now);
        });
    }

    public ScrapeProgressDTO get(String jobId) {
        return jobs.get(jobId);
    }

    @Scheduled(fixedRate = 300000L)
    void evictStaleJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(TTL_MINUTES);
        int before = jobs.size();
        jobs.entrySet().removeIf(entry -> {
            ScrapeProgressDTO dto = entry.getValue();
            if (dto.getFinishedAt() != null) {
                return dto.getFinishedAt().isBefore(cutoff);
            }
            return dto.getStartedAt() != null && dto.getStartedAt().isBefore(cutoff);
        });
        int after = jobs.size();
        if (before != after) {
            log.debug("ScrapeProgressTracker evicted {} stale jobs (was {} now {})", before - after, before, after);
        }
    }

    private void update(String jobId, java.util.function.Consumer<ScrapeProgressDTO> fn) {
        ScrapeProgressDTO dto = jobs.get(jobId);
        if (dto == null) {
            log.warn("ScrapeProgressTracker.update: jobId {} not found (already evicted?). No-op.", jobId);
            return;
        }
        fn.accept(dto);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
