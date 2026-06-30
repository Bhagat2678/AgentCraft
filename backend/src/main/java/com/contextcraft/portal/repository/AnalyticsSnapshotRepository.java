package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.AnalyticsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalyticsSnapshotRepository extends JpaRepository<AnalyticsSnapshot, UUID> {

    Optional<AnalyticsSnapshot> findByBusinessIdAndPeriod(UUID businessId, LocalDate period);
}
