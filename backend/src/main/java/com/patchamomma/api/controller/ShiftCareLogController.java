package com.patchamomma.api.controller;

import com.patchamomma.api.model.Booking;
import com.patchamomma.api.model.CaregiverProfile;
import com.patchamomma.api.model.ShiftCareLog;
import com.patchamomma.api.repository.BookingRepository;
import com.patchamomma.api.repository.ShiftCareLogRepository;
import com.patchamomma.api.service.GeminiVitalsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings/{bookingId}/vitals")
@RequiredArgsConstructor
public class ShiftCareLogController {

    private final ShiftCareLogRepository shiftCareLogRepository;
    private final BookingRepository bookingRepository;
    private final GeminiVitalsService geminiVitalsService;

    @GetMapping
    public ResponseEntity<List<ShiftCareLog>> getVitalsForBooking(@PathVariable("bookingId") UUID bookingId) {
        return ResponseEntity.ok(shiftCareLogRepository.findByBookingBookingIdOrderByLoggedAtDesc(bookingId));
    }

    @PostMapping
    public ResponseEntity<ShiftCareLog> createVitalsLog(
            @PathVariable("bookingId") UUID bookingId,
            @RequestBody ShiftCareLog logRequest
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        CaregiverProfile caregiver = booking.getCaregiverProfile();

        logRequest.setBooking(booking);
        logRequest.setCaregiverProfile(caregiver);

        ShiftCareLog saved = shiftCareLogRepository.save(logRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/parse-voice")
    public ResponseEntity<ShiftCareLog> parseVoiceAndCreateLog(
            @PathVariable("bookingId") UUID bookingId,
            @RequestBody VoiceLogRequest request
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        GeminiVitalsService.ParsedVitalsResult parsed = geminiVitalsService.parseVoiceNote(request.getRawVoiceNote());

        ShiftCareLog log = ShiftCareLog.builder()
                .booking(booking)
                .caregiverProfile(booking.getCaregiverProfile())
                .systolicBp(parsed.getSystolicBp())
                .diastolicBp(parsed.getDiastolicBp())
                .bloodSugarMgDl(parsed.getBloodSugarMgDl())
                .medicationsGiven(parsed.getMedicationsGiven())
                .mealTaken(parsed.getMealTaken())
                .rawVoiceNote(request.getRawVoiceNote())
                .summaryNotes(parsed.getSummaryNotes())
                .build();

        ShiftCareLog saved = shiftCareLogRepository.save(log);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Data
    public static class VoiceLogRequest {
        private String rawVoiceNote;
    }
}
