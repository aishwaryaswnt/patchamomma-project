package com.patchamomma.api.controller;

import com.patchamomma.api.model.*;
import com.patchamomma.api.repository.AvailabilitySlotRepository;
import com.patchamomma.api.repository.BookingRepository;
import com.patchamomma.api.repository.CaregiverProfileRepository;
import com.patchamomma.api.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingRepository bookingRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final UserRepository userRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private static final SecureRandom random = new SecureRandom();

    private String generateOtp() {
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    @GetMapping
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBookingById(@PathVariable("id") UUID id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        return ResponseEntity.ok(booking);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Booking> createBooking(@RequestBody Booking bookingRequest) {
        if (bookingRequest.getAvailabilitySlot() == null || bookingRequest.getAvailabilitySlot().getSlotId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "availabilitySlot.slotId is required");
        }
        if (bookingRequest.getRecipient() == null || bookingRequest.getRecipient().getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipient.userId is required");
        }
        if (bookingRequest.getCaregiverProfile() == null || bookingRequest.getCaregiverProfile().getCaregiverId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "caregiverProfile.caregiverId is required");
        }

        UUID slotId = bookingRequest.getAvailabilitySlot().getSlotId();

        AvailabilitySlot slot = availabilitySlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability slot not found"));

        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot is no longer available");
        }

        User recipient = userRepository.findById(bookingRequest.getRecipient().getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient user not found"));
        CaregiverProfile caregiver = caregiverProfileRepository.findById(bookingRequest.getCaregiverProfile().getCaregiverId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver profile not found"));

        if (slot.getCaregiverProfile() != null
                && !slot.getCaregiverProfile().getCaregiverId().equals(caregiver.getCaregiverId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot does not belong to this caregiver");
        }

        slot.setStatus(SlotStatus.BOOKED);
        availabilitySlotRepository.save(slot);

        Booking booking = Booking.builder()
                .availabilitySlot(slot)
                .recipient(recipient)
                .caregiverProfile(caregiver)
                .startOtp(generateOtp())
                .startOtpAttempts(0)
                .endOtpAttempts(0)
                .maxAttempts(5)
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking saved = bookingRepository.save(booking);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}/otp-status")
    public ResponseEntity<Map<String, Object>> getOtpStatus(@PathVariable("id") UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        AvailabilitySlot slot = booking.getAvailabilitySlot();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startTime = slot.getStartTime();
        OffsetDateTime endTime = slot.getEndTime();

        Map<String, Object> response = new HashMap<>();
        response.put("bookingId", booking.getBookingId());
        response.put("status", booking.getStatus());

        // Start OTP Window: start_time - 30m <= now <= start_time + 1h
        boolean startOtpVisible = now.isAfter(startTime.minusMinutes(30)) && now.isBefore(startTime.plusHours(1));
        response.put("startOtpVisible", startOtpVisible);
        if (startOtpVisible && booking.getStatus() == BookingStatus.CONFIRMED) {
            response.put("startOtp", booking.getStartOtp());
        }

        // End OTP Window: end_time - 30m <= now <= end_time + 1h
        boolean endOtpVisible = now.isAfter(endTime.minusMinutes(30)) && now.isBefore(endTime.plusHours(1));
        response.put("endOtpVisible", endOtpVisible);
        if (endOtpVisible && booking.getStatus() == BookingStatus.IN_PROGRESS) {
            response.put("endOtp", booking.getEndOtp());
        }

        response.put("startTime", startTime);
        response.put("endTime", endTime);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/verify-start")
    @Transactional
    public ResponseEntity<Map<String, Object>> verifyStartOtp(
            @PathVariable("id") UUID bookingId,
            @RequestBody OtpRequest request
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not in CONFIRMED state");
        }

        if (booking.getStartOtpAttempts() >= booking.getMaxAttempts()) {
            booking.setStatus(BookingStatus.FAILED_VERIFICATION);
            bookingRepository.save(booking);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Maximum OTP verification attempts exceeded");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startTime = booking.getAvailabilitySlot().getStartTime();
        OffsetDateTime minStart = startTime.minusMinutes(30);
        OffsetDateTime maxStart = startTime.plusHours(1);

        if (now.isBefore(minStart) || now.isAfter(maxStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service initiation allowed only within 30 minutes before to 1 hour after start time");
        }

        Map<String, Object> response = new HashMap<>();

        if (booking.getStartOtp().equals(request.getOtp())) {
            booking.setStatus(BookingStatus.IN_PROGRESS);
            booking.setStartedAt(now);
            booking.setEndOtp(generateOtp()); // Generate distinct OTP for termination
            bookingRepository.save(booking);

            response.put("success", true);
            response.put("message", "Service session initiated successfully");
            response.put("bookingStatus", booking.getStatus());
            response.put("startedAt", booking.getStartedAt());
            return ResponseEntity.ok(response);
        } else {
            int attempts = booking.getStartOtpAttempts() + 1;
            booking.setStartOtpAttempts(attempts);

            if (attempts >= booking.getMaxAttempts()) {
                booking.setStatus(BookingStatus.FAILED_VERIFICATION);
                bookingRepository.save(booking);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Maximum attempts exceeded. Session locked.");
            } else {
                bookingRepository.save(booking);
                int remaining = booking.getMaxAttempts() - attempts;
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Start OTP. Remaining attempts: " + remaining);
            }
        }
    }

    @PostMapping("/{id}/verify-end")
    @Transactional
    public ResponseEntity<Map<String, Object>> verifyEndOtp(
            @PathVariable("id") UUID bookingId,
            @RequestBody OtpRequest request
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not IN_PROGRESS");
        }

        if (booking.getEndOtpAttempts() >= booking.getMaxAttempts()) {
            booking.setStatus(BookingStatus.FAILED_VERIFICATION);
            bookingRepository.save(booking);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Maximum OTP verification attempts exceeded");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime endTime = booking.getAvailabilitySlot().getEndTime();
        OffsetDateTime minEnd = endTime.minusMinutes(30);
        OffsetDateTime maxEnd = endTime.plusHours(1);

        if (now.isBefore(minEnd) || now.isAfter(maxEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service termination allowed only within 30 minutes before to 1 hour after end time");
        }

        Map<String, Object> response = new HashMap<>();

        if (booking.getEndOtp().equals(request.getOtp())) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setEndedAt(now);
            bookingRepository.save(booking);

            response.put("success", true);
            response.put("message", "Service session completed successfully");
            response.put("bookingStatus", booking.getStatus());
            response.put("endedAt", booking.getEndedAt());
            return ResponseEntity.ok(response);
        } else {
            int attempts = booking.getEndOtpAttempts() + 1;
            booking.setEndOtpAttempts(attempts);

            if (attempts >= booking.getMaxAttempts()) {
                booking.setStatus(BookingStatus.FAILED_VERIFICATION);
                bookingRepository.save(booking);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Maximum attempts exceeded. Session locked.");
            } else {
                bookingRepository.save(booking);
                int remaining = booking.getMaxAttempts() - attempts;
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid End OTP. Remaining attempts: " + remaining);
            }
        }
    }

    @Data
    public static class OtpRequest {
        private String otp;
    }
}
