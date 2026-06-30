package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, UUID> {

    List<TaskAssignment> findByAssigneeId(UUID assigneeId);

    List<TaskAssignment> findByTaskId(UUID taskId);

    List<TaskAssignment> findByTaskIdAndCompletedAtIsNull(UUID taskId);
}
