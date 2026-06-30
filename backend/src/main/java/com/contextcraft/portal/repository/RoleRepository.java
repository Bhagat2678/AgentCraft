package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByBusinessId(UUID businessId);

    List<Role> findByBusinessIdAndIsDefault(UUID businessId, boolean isDefault);
}
