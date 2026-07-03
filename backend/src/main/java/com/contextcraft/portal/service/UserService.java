package com.contextcraft.portal.service;

import com.contextcraft.portal.entity.*;
import com.contextcraft.portal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
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

    public UserService(UserRepository userRepository,
                       UserPhoneRepository userPhoneRepository,
                       TelegramUserRepository telegramUserRepository,
                       BusinessRepository businessRepository,
                       RoleRepository roleRepository,
                       UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userPhoneRepository = userPhoneRepository;
        this.telegramUserRepository = telegramUserRepository;
        this.businessRepository = businessRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User findByPhone(String phoneNumber) {
        return userPhoneRepository.findByPhoneNumber(phoneNumber)
                .map(UserPhone::getUser)
                .orElseThrow(() -> new RuntimeException("No user with phone: " + phoneNumber));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByTelegramChatId(Long chatId) {
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
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found: " + businessId));

        User user = new User();
        user.setBusiness(business);
        user.setDisplayName(username != null ? username : "User-" + chatId);
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
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found: " + businessId));

        // Upsert user record
        User user;
        if (userPhoneRepository.existsByPhoneNumber(phoneNumber)) {
            user = userPhoneRepository.findByPhoneNumber(phoneNumber).get().getUser();
        } else {
            user = new User();
            user.setBusiness(business);
            user.setStatus("PENDING");
            user = userRepository.save(user);

            UserPhone phone = new UserPhone();
            phone.setUser(user);
            phone.setPhoneNumber(phoneNumber);
            phone.setPrimary(true);
            userPhoneRepository.save(phone);
        }

        // Generate secure 32-byte invite token
        byte[] tokenBytes = new byte[32];
        new SecureRandom().nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        UserPhone phone = userPhoneRepository.findByPhoneNumber(phoneNumber).get();
        phone.setInviteToken(token);
        phone.setInviteExpires(OffsetDateTime.now().plusHours(48));
        userPhoneRepository.save(phone);

        // Pre-assign role so it's ready when they accept
        if (roleId != null) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
            Department dept = departmentId != null
                    ? new Department() {{ setId(departmentId); }}
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

    /**
     * Accepts an invite by token: activates the user, sets verifiedAt, clears token.
     */
    public User acceptInvite(String token) {
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

    @Transactional(readOnly = true)
    public List<User> listByBusiness(UUID businessId) {
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
            Department dept = new Department();
            dept.setId(departmentId);
            ur.setDepartment(dept);
        }
        userRoleRepository.save(ur);
    }
}
