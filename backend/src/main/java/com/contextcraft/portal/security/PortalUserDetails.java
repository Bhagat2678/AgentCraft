package com.contextcraft.portal.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Custom Spring Security principal that holds our domain-specific
 * userId and businessId alongside the standard UserDetails contract.
 */
public class PortalUserDetails implements UserDetails {

    private final UUID userId;
    private final UUID businessId;
    private final String phoneNumber;
    private final List<GrantedAuthority> authorities;

    public PortalUserDetails(UUID userId, UUID businessId, String phoneNumber, List<GrantedAuthority> authorities) {
        this.userId = userId;
        this.businessId = businessId;
        this.phoneNumber = phoneNumber;
        this.authorities = authorities;
    }

    public UUID getUserId() { return userId; }
    public UUID getBusinessId() { return businessId; }
    public String getPhoneNumber() { return phoneNumber; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return null; } // No password — WhatsApp-only auth
    @Override public String getUsername() { return phoneNumber; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
