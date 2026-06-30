package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByBusinessId(UUID businessId);

    List<Department> findByBusinessIdAndParentDeptIsNull(UUID businessId);
}
