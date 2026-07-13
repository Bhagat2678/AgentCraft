package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByBusinessId(UUID businessId);

    List<User> findByBusinessIdAndStatus(UUID businessId, String status);

    /** Used by the portal login flow: match by email within a named business. */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.business.name = :businessName")
    Optional<User> findByEmailAndBusinessName(
            @Param("email") String email,
            @Param("businessName") String businessName);
}
