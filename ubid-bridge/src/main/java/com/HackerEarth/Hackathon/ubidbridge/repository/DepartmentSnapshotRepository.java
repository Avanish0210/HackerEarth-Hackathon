package com.HackerEarth.Hackathon.ubidbridge.repository;

import com.HackerEarth.Hackathon.ubidbridge.entity.DepartmentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentSnapshotRepository extends JpaRepository<DepartmentSnapshot, Long> {
    // Last known snapshot for a specific field — used for hash diff
    Optional<DepartmentSnapshot> findByDepartmentIdAndUbidAndFieldName(
            String departmentId, String ubid, String fieldName);

    // All snapshot fields for a department+UBID combination
    List<DepartmentSnapshot> findByDepartmentIdAndUbid(String departmentId, String ubid);
}
