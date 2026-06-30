package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.UserRole;
import com.contextcraft.portal.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("SELECT ur.role.id FROM UserRole ur WHERE ur.user.id = :userId AND ur.user.business.id = :businessId")
    List<UUID> findRoleIdsByUserAndBusiness(@Param("userId") UUID userId, @Param("businessId") UUID businessId);

    @Query("SELECT MIN(ur.role.level) FROM UserRole ur WHERE ur.user.id = :userId AND ur.user.business.id = :businessId")
    Integer getMinRoleLevel(@Param("userId") UUID userId, @Param("businessId") UUID businessId);

    List<UserRole> findByUserId(UUID userId);
}
