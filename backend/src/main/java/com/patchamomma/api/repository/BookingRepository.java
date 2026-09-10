package com.patchamomma.api.repository;

import com.patchamomma.api.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByRecipientUserId(UUID recipientId);
    List<Booking> findByCaregiverProfileCaregiverId(UUID caregiverId);
}
