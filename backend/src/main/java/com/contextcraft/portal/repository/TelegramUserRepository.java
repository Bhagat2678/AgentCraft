package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.TelegramUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelegramUserRepository extends JpaRepository<TelegramUser, UUID> {

    Optional<TelegramUser> findByChatId(Long chatId);

    Optional<TelegramUser> findByUsername(String username);

    Optional<TelegramUser> findByUser(com.contextcraft.portal.entity.User user);

    boolean existsByChatId(Long chatId);
}
