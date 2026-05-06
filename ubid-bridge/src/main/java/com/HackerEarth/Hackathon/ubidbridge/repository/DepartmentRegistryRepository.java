package com.HackerEarth.Hackathon.ubidbridge.repository;

import com.HackerEarth.Hackathon.ubidbridge.entity.DepartmentRegistry;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRegistryRepository extends JpaRepositoryImplementation<DepartmentRegistry, Long> {
    // All active departments that carry a record for this UBID
    List<DepartmentRegistry> findByUbidAndActiveTrue(String ubid);

    // All active departments regardless of UBID (for poller)
    List<DepartmentRegistry> findByActiveTrue();

    // Check if a department is registered for a UBID
    boolean existsByUbidAndDepartmentId(String ubid, String departmentId);
}
