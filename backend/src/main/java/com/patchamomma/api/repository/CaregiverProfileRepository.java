package com.patchamomma.api.repository;

import com.patchamomma.api.model.CaregiverProfile;
import com.patchamomma.api.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaregiverProfileRepository extends JpaRepository<CaregiverProfile, UUID> {

    Optional<CaregiverProfile> findByUserUserId(UUID userId);

    List<CaregiverProfile> findByStatus(VerificationStatus status);

    @Query("""
        SELECT DISTINCT cp 
        FROM CaregiverProfile cp
        JOIN cp.skills s
        JOIN AvailabilitySlot slot ON slot.caregiverProfile = cp
        WHERE cp.status = 'APPROVED'
          AND s.skillId IN :skillIds
          AND slot.status = 'OPEN'
          AND slot.startTime >= :startTime
          AND slot.endTime <= :endTime
    """)
    List<CaregiverProfile> findMatchingCaregivers(
            @Param("skillIds") List<UUID> skillIds,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
}
