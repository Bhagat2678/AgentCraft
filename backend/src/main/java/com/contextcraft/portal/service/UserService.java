package com.contextcraft.portal.service;

import com.contextcraft.portal.entity.*;
import com.contextcraft.portal.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for user onboarding, invitations, role management, and status updates.
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserPhoneRepository userPhoneRepository;
    private final TelegramUserRepository telegramUserRepository;
    private final BusinessRepository businessRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       UserPhoneRepository userPhoneRepository,
                       TelegramUserRepository telegramUserRepository,
                       BusinessRepository businessRepository,
                       RoleRepository roleRepository,
                       UserRoleRepository userRoleRepository,
                       DepartmentRepository departmentRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userPhoneRepository = userPhoneRepository;
        this.telegramUserRepository = telegramUserRepository;
        this.businessRepository = businessRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("User id is required");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User findByPhone(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        return userPhoneRepository.findByPhoneNumber(normalizedPhone)
                .map(UserPhone::getUser)
                .orElseThrow(() -> new RuntimeException("No user with phone: " + normalizedPhone));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByTelegramChatId(Long chatId) {
        if (chatId == null || chatId <= 0) {
            return Optional.empty();
        }
        return telegramUserRepository.findByChatId(chatId)
                .map(TelegramUser::getUser);
    }

    /**
     * Creates a new ACTIVE user for a Telegram-based portal creation.
     * Links the user to the given business and Telegram chatId/username.
     *
     * @return the created User
     */
    public User createTelegramUser(UUID businessId, Long chatId, String username) {
        if (businessId == null) {
            throw new IllegalArgumentException("Business id is required");
        }
        if (chatId == null || chatId <= 0) {
            throw new IllegalArgumentException("A valid Telegram chat id is required");
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found: " + businessId));

        User user = new User();
        user.setBusiness(business);
        user.setDisplayName(username != null && !username.isBlank() ? username : "User-" + chatId);
        user.setStatus("ACTIVE");
        user = userRepository.save(user);

        TelegramUser tgUser = new TelegramUser();
        tgUser.setUser(user);
        tgUser.setChatId(chatId);
        tgUser.setUsername(username);
        tgUser.setVerifiedAt(OffsetDateTime.now());
        telegramUserRepository.save(tgUser);

        return user;
    }

    /**
     * Creates a new PENDING user with a phone and sends an invite token.
     * Returns the invite token so the caller (FSM or REST layer) can dispatch it via WhatsApp.
     */
    public String inviteUser(UUID businessId, String phoneNumber, UUID roleId,
                             UUID departmentId, UUID invitedBy) {
        if (businessId == null) {
            throw new IllegalArgumentException("Business id is required");
        }
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found: " + businessId));

        // Upsert user record
        User user;
        if (userPhoneRepository.existsByPhoneNumber(normalizedPhone)) {
            user = userPhoneRepository.findByPhoneNumber(normalizedPhone).get().getUser();
        } else {
            user = new User();
            user.setBusiness(business);
            user.setStatus("PENDING");
            user = userRepository.save(user);

            UserPhone phone = new UserPhone();
            phone.setUser(user);
            phone.setPhoneNumber(normalizedPhone);
            phone.setPrimary(true);
            userPhoneRepository.save(phone);
        }

        // Generate secure 32-byte invite token
        byte[] tokenBytes = new byte[32];
        new SecureRandom().nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        UserPhone phone = userPhoneRepository.findByPhoneNumber(normalizedPhone).orElseThrow(
                () -> new RuntimeException("Phone record not found for: " + normalizedPhone));
        phone.setInviteToken(token);
        phone.setInviteExpires(OffsetDateTime.now().plusHours(48));
        userPhoneRepository.save(phone);

        // Pre-assign role so it's ready when they accept
        if (roleId != null) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
            Department dept = departmentId != null
                    ? departmentRepository.getReferenceById(departmentId)
                    : null;

            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(role);
            ur.setDepartment(dept);
            ur.setAssignedBy(invitedBy);
            userRoleRepository.save(ur);
        }

        return token;
    }

    public User invitePortalUser(UUID businessId, String email, String phoneNumber, String displayName,
                                 UUID roleId, UUID departmentId, UUID invitedBy) {
        String contactPhone = phoneNumber;
        if (contactPhone == null || contactPhone.isBlank()) {
            long syntheticSuffix = Math.abs((long) (email != null ? email : UUID.randomUUID().toString()).hashCode());
            contactPhone = "+1000000" + syntheticSuffix;
        }
        inviteUser(businessId, contactPhone, roleId, departmentId, invitedBy);
        User user = userPhoneRepository.findByPhoneNumber(contactPhone)
                .map(UserPhone::getUser)
                .orElseThrow(() -> new RuntimeException("Invited user not found"));
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (email != null && !email.isBlank()) {
            user.setEmail(normalizeEmail(email));
        }
        return userRepository.save(user);
    }

    /**
     * Accepts an invite by token: activates the user, sets verifiedAt, clears token.
     */
    public User acceptInvite(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invite token is required");
        }

        UserPhone phone = userPhoneRepository.findByInviteToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        if (phone.getInviteExpires() != null && phone.getInviteExpires().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Invite token has expired");
        }

        User user = phone.getUser();
        user.setStatus("ACTIVE");
        userRepository.save(user);

        phone.setVerifiedAt(OffsetDateTime.now());
        phone.setInviteToken(null);
        phone.setInviteExpires(null);
        userPhoneRepository.save(phone);

        return user;
    }

    /**
     * Accepts an invite by token, activates the user, and registers their Telegram chat ID.
     */
    public User acceptInviteTelegram(String token, Long chatId, String username) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invite token is required");
        }
        if (chatId == null || chatId <= 0) {
            throw new IllegalArgumentException("A valid Telegram chat id is required");
        }

        UserPhone phone = userPhoneRepository.findByInviteToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        if (phone.getInviteExpires() != null && phone.getInviteExpires().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Invite token has expired");
        }

        User user = phone.getUser();
        user.setStatus("ACTIVE");
        userRepository.save(user);

        phone.setVerifiedAt(OffsetDateTime.now());
        phone.setInviteToken(null);
        phone.setInviteExpires(null);
        userPhoneRepository.save(phone);

        // Link Telegram user if not already linked
        if (!telegramUserRepository.existsByChatId(chatId)) {
            TelegramUser tgUser = new TelegramUser();
            tgUser.setUser(user);
            tgUser.setChatId(chatId);
            tgUser.setUsername(username);
            tgUser.setVerifiedAt(OffsetDateTime.now());
            telegramUserRepository.save(tgUser);
        }

        return user;
    }

    /** Suspends a user (no data deletion — GDPR-safe). */
    public void suspendUser(UUID userId) {
        User user = getById(userId);
        user.setStatus("SUSPENDED");
        userRepository.save(user);
    }

    public User updateStatus(UUID userId, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        User user = getById(userId);
        user.setStatus(status.trim().toUpperCase(Locale.ROOT));
        return userRepository.save(user);
    }

    public User updateProfile(UUID userId, String displayName, String email) {
        User user = getById(userId);
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (email != null && !email.isBlank()) {
            user.setEmail(normalizeEmail(email));
        }
        return userRepository.save(user);
    }

    /**
     * Authenticates a user by email + business name + portal password.
     * Looks up the user by email field (stored on User) and validates against the business name.
     * Password hashing should be added in Phase 4 (Security hardening).
     *
     * @throws RuntimeException if the credentials are invalid
     */
    @Transactional(readOnly = true)
    public User loginByEmailAndPortalName(String email, String businessName, String password) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedBusinessName = normalizeBusinessName(businessName);

        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (normalizedBusinessName == null || normalizedBusinessName.isBlank()) {
            throw new RuntimeException("Business name is required");
        }
        if (password == null || password.isBlank()) {
            throw new RuntimeException("Password is required");
        }

        User user = userRepository.findByEmailAndBusinessName(normalizedEmail, normalizedBusinessName)
                .orElseThrow(() -> new RuntimeException(
                        "No account found for email '" + normalizedEmail + "' in portal '" + normalizedBusinessName + "'"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Account is not active. Please contact your admin.");
        }

        Business business = user.getBusiness();
        if (business == null) {
            throw new RuntimeException("Account is not linked to a business.");
        }

        if (!isPortalPasswordValid(business.getPortalPassword(), password)) {
            throw new RuntimeException("Invalid password.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public List<User> listByBusiness(UUID businessId) {
        if (businessId == null) {
            throw new IllegalArgumentException("Business id is required");
        }
        return userRepository.findByBusinessId(businessId);
    }

    /**
     * Assigns a role to a user. Idempotent — does nothing if already assigned.
     */
    public void assignRole(UUID userId, UUID roleId, UUID departmentId, UUID assignedBy) {
        User user = getById(userId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        UserRoleId id = new UserRoleId(userId, roleId);
        if (userRoleRepository.existsById(id)) return; // already assigned

        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        ur.setAssignedBy(assignedBy);
        if (departmentId != null) {
            Department dept = departmentRepository.getReferenceById(departmentId);
            ur.setDepartment(dept);
        }
        userRoleRepository.save(ur);
    }

    private boolean isPortalPasswordValid(String storedPassword, String suppliedPassword) {
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }

        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(suppliedPassword, storedPassword);
        }

        return storedPassword.equals(suppliedPassword);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String normalized = phoneNumber.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBusinessName(String businessName) {
        if (businessName == null) {
            return null;
        }
        return businessName.trim();
    }
}
