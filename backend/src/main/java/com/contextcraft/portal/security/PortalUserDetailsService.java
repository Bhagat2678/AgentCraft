package com.contextcraft.portal.security;

import com.contextcraft.portal.entity.TelegramUser;
import com.contextcraft.portal.entity.User;
import com.contextcraft.portal.entity.UserPhone;
import com.contextcraft.portal.repository.TelegramUserRepository;
import com.contextcraft.portal.repository.UserPhoneRepository;
import com.contextcraft.portal.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Loads a PortalUserDetails given a phone number (used in test/internal flows)
 * or a userId string (used by the JWT filter after token parsing).
 */
@Service
@Transactional(readOnly = true)
public class PortalUserDetailsService implements UserDetailsService {

    private final UserPhoneRepository userPhoneRepository;
    private final UserRepository userRepository;
    private final TelegramUserRepository telegramUserRepository;

    public PortalUserDetailsService(UserPhoneRepository userPhoneRepository,
                                    UserRepository userRepository,
                                    TelegramUserRepository telegramUserRepository) {
        this.userPhoneRepository = userPhoneRepository;
        this.userRepository = userRepository;
        this.telegramUserRepository = telegramUserRepository;
    }

    /** Used by Spring Security — username is the E.164 phone number. */
    @Override
    public UserDetails loadUserByUsername(String phoneNumber) throws UsernameNotFoundException {
        if (phoneNumber.startsWith("telegram:")) {
            try {
                Long chatId = Long.parseLong(phoneNumber.substring(9));
                TelegramUser tgUser = telegramUserRepository.findByChatId(chatId)
                        .orElseThrow(() -> new UsernameNotFoundException("No Telegram user with chatId: " + chatId));
                return buildDetails(tgUser.getUser());
            } catch (NumberFormatException e) {
                throw new UsernameNotFoundException("Invalid Telegram format: " + phoneNumber);
            }
        }
        UserPhone phone = userPhoneRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new UsernameNotFoundException("No user with phone: " + phoneNumber));
        return buildDetails(phone.getUser());
    }

    /** Used by the JWT filter — loads by userId parsed from the token. */
    public PortalUserDetails loadUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        return buildDetails(user);
    }

    private PortalUserDetails buildDetails(User user) {
        String primaryPhone = null;
        if (user.getPhones() != null) {
            primaryPhone = user.getPhones().stream()
                    .filter(UserPhone::isPrimary)
                    .map(UserPhone::getPhoneNumber)
                    .findFirst()
                    .orElse(null);
        }

        if (primaryPhone == null) {
            primaryPhone = telegramUserRepository.findByUser(user)
                    .map(tg -> "telegram:" + tg.getChatId())
                    .orElse(null);
        }

        return new PortalUserDetails(
                user.getId(),
                user.getBusiness().getId(),
                primaryPhone,
                List.of() // Authorities loaded lazily via PermissionEvaluator, not GrantedAuthority
        );
    }
}
