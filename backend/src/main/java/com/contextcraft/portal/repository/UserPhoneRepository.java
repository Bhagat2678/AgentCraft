package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.UserPhone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPhoneRepository extends JpaRepository<UserPhone, UUID> {

    Optional<UserPhone> findByPhoneNumber(String phoneNumber);

    Optional<UserPhone> findByUserIdAndIsPrimaryTrue(UUID userId);

    Optional<UserPhone> findByInviteToken(String token);

    boolean existsByPhoneNumber(String phoneNumber);
}
