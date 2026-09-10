package com.patchamomma.api.repository;

import com.patchamomma.api.model.AvailabilitySlot;
import com.patchamomma.api.model.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    List<AvailabilitySlot> findByCaregiverProfileCaregiverIdAndStatus(UUID caregiverId, SlotStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AvailabilitySlot s WHERE s.slotId = :slotId")
    Optional<AvailabilitySlot> findByIdWithLock(@Param("slotId") UUID slotId);
}
