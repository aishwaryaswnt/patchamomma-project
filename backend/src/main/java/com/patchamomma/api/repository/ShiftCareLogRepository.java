package com.patchamomma.api.repository;

import com.patchamomma.api.model.ShiftCareLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShiftCareLogRepository extends JpaRepository<ShiftCareLog, UUID> {
    List<ShiftCareLog> findByBookingBookingIdOrderByLoggedAtDesc(UUID bookingId);
    List<ShiftCareLog> findByCaregiverProfileCaregiverIdOrderByLoggedAtDesc(UUID caregiverId);
}
