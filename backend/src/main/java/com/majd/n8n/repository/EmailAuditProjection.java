package com.majd.n8n.repository;

public interface EmailAuditProjection {
    Long getId();

    Long getLeadId();

    String getInstitutionName();

    String getEmail();

    Boolean getIsPrimary();

    Long getDuplicateCount();
}
