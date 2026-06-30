package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    Optional<RolePermission> findByRoleIdAndPermission(UUID roleId, String permission);

    void deleteByRoleId(UUID roleId);
}
