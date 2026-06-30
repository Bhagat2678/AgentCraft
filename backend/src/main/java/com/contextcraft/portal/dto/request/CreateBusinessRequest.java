package com.contextcraft.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new business portal.
 */
public class CreateBusinessRequest {

    @NotBlank(message = "Business name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @NotBlank(message = "Business type is required")
    @Pattern(regexp = "RETAIL|SERVICES|MANUFACTURING|OTHER", message = "Type must be one of: RETAIL, SERVICES, MANUFACTURING, OTHER")
    private String type;

    private String industry;
    private String location;
    private String basePolicies;
    private String wabaPhoneId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getBasePolicies() { return basePolicies; }
    public void setBasePolicies(String basePolicies) { this.basePolicies = basePolicies; }
    public String getWabaPhoneId() { return wabaPhoneId; }
    public void setWabaPhoneId(String wabaPhoneId) { this.wabaPhoneId = wabaPhoneId; }
}
