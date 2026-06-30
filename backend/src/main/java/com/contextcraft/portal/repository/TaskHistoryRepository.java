package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.TaskHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskHistoryRepository extends JpaRepository<TaskHistory, UUID> {

    List<TaskHistory> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
