package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByBusinessId(UUID businessId);

    List<User> findByBusinessIdAndStatus(UUID businessId, String status);
}
