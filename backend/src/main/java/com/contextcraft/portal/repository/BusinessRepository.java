package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessRepository extends JpaRepository<Business, UUID> {

    Optional<Business> findByIdAndDeletedAtIsNull(UUID id);

    List<Business> findAllByDeletedAtIsNull();
}
