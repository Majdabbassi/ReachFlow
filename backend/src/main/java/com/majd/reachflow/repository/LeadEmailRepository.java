package com.majd.reachflow.repository;

import com.majd.reachflow.entity.LeadEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface LeadEmailRepository extends JpaRepository<LeadEmail, Long> {
    List<LeadEmail> findByLeadId(Long leadId);

    List<LeadEmail> findByLeadIdIn(List<Long> leadIds);

    long countByLeadId(Long leadId);

    @Query(value = """
            SELECT
                le.id AS id,
                l.id AS leadId,
                l.institution_name AS institutionName,
                le.email AS email,
                le.is_primary AS isPrimary,
                0 AS duplicateCount
            FROM lead_emails le
            JOIN leads l ON l.id = le.lead_id
            WHERE le.email IS NULL
               OR TRIM(le.email) = ''
                    OR LOWER(TRIM(le.email)) NOT REGEXP '^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$'
                    OR LOWER(TRIM(le.email)) REGEXP '\\.(webp|png|jpg|jpeg|svg|gif)$'
                    OR LOWER(TRIM(le.email)) LIKE '%media%'
                    OR LOWER(TRIM(le.email)) LIKE '%@2x%'
                    OR LOWER(TRIM(le.email)) LIKE '%query%'
                    OR LOWER(TRIM(le.email)) LIKE '%--%'
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM lead_emails le
            WHERE le.email IS NULL
               OR TRIM(le.email) = ''
                    OR LOWER(TRIM(le.email)) NOT REGEXP '^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$'
                    OR LOWER(TRIM(le.email)) REGEXP '\\.(webp|png|jpg|jpeg|svg|gif)$'
                    OR LOWER(TRIM(le.email)) LIKE '%media%'
                    OR LOWER(TRIM(le.email)) LIKE '%@2x%'
                    OR LOWER(TRIM(le.email)) LIKE '%query%'
                    OR LOWER(TRIM(le.email)) LIKE '%--%'
            """,
            nativeQuery = true)
    Page<EmailAuditProjection> findInvalidEmails(Pageable pageable);

    @Query(value = """
            SELECT
                le.id AS id,
                l.id AS leadId,
                l.institution_name AS institutionName,
                le.email AS email,
                le.is_primary AS isPrimary,
                dup.duplicate_count AS duplicateCount
            FROM lead_emails le
            JOIN leads l ON l.id = le.lead_id
            JOIN (
                SELECT LOWER(TRIM(email)) AS normalized_email, COUNT(*) AS duplicate_count
                FROM lead_emails
                GROUP BY LOWER(TRIM(email))
                HAVING COUNT(*) > 1
            ) dup ON dup.normalized_email = LOWER(TRIM(le.email))
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM lead_emails le
            JOIN (
                SELECT LOWER(TRIM(email)) AS normalized_email
                FROM lead_emails
                GROUP BY LOWER(TRIM(email))
                HAVING COUNT(*) > 1
            ) dup ON dup.normalized_email = LOWER(TRIM(le.email))
            """,
            nativeQuery = true)
    Page<EmailAuditProjection> findDuplicateEmails(Pageable pageable);

    Optional<LeadEmail> findByLeadIdAndIsPrimaryTrue(Long leadId);
    boolean existsByLeadIdAndEmail(Long leadId, String email);
    Optional<LeadEmail> findByEmail(String email);

    @Query("SELECT le FROM LeadEmail le WHERE LOWER(TRIM(le.email)) IN :emails")
    List<LeadEmail> findAllByNormalizedEmailIn(@Param("emails") Set<String> emails);
}
