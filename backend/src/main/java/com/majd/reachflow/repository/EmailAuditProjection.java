package com.majd.reachflow.repository;

public interface EmailAuditProjection {
    Long getId();

    Long getLeadId();

    String getInstitutionName();

    String getEmail();

    Boolean getIsPrimary();

    Long getDuplicateCount();
}
