package com.contextcraft.portal.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for business portal data.
 */
public class BusinessResponse {

    private UUID id;
    private String name;
    private String type;
    private String industry;
    private String location;
    private String basePolicies;
    private UUID ownerUserId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static BusinessResponse from(com.contextcraft.portal.entity.Business b) {
        BusinessResponse r = new BusinessResponse();
        r.id = b.getId();
        r.name = b.getName();
        r.type = b.getType();
        r.industry = b.getIndustry();
        r.location = b.getLocation();
        r.basePolicies = b.getBasePolicies();
        r.ownerUserId = b.getOwnerUserId();
        r.createdAt = b.getCreatedAt();
        r.updatedAt = b.getUpdatedAt();
        return r;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getIndustry() { return industry; }
    public String getLocation() { return location; }
    public String getBasePolicies() { return basePolicies; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
