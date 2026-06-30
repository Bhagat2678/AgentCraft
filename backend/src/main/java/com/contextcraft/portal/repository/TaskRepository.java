package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByBusinessId(UUID businessId);

    List<Task> findByBusinessIdAndStatus(UUID businessId, String status);

    List<Task> findByBusinessIdAndPriority(UUID businessId, String priority);

    long countByBusinessIdAndStatus(UUID businessId, String status);
}
