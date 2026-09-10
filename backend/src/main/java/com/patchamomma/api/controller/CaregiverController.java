package com.patchamomma.api.controller;

import com.patchamomma.api.model.AvailabilitySlot;
import com.patchamomma.api.model.CaregiverProfile;
import com.patchamomma.api.model.Skill;
import com.patchamomma.api.model.SlotStatus;
import com.patchamomma.api.repository.AvailabilitySlotRepository;
import com.patchamomma.api.repository.CaregiverProfileRepository;
import com.patchamomma.api.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/caregivers")
@RequiredArgsConstructor
public class CaregiverController {

    private final CaregiverProfileRepository caregiverProfileRepository;
    private final SkillRepository skillRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;

    @GetMapping
    public ResponseEntity<List<CaregiverProfile>> getAllCaregivers() {
        return ResponseEntity.ok(caregiverProfileRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaregiverProfile> getCaregiverById(@PathVariable("id") UUID id) {
        CaregiverProfile profile = caregiverProfileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver profile not found"));
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<CaregiverProfile> getCaregiverByUserId(@PathVariable("userId") UUID userId) {
        CaregiverProfile profile = caregiverProfileRepository.findByUserUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver profile not found for user"));
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/search")
    public ResponseEntity<List<CaregiverProfile>> searchCaregivers(
            @RequestParam("skillIds") List<UUID> skillIds,
            @RequestParam("startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam("endTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime
    ) {
        return ResponseEntity.ok(caregiverProfileRepository.findMatchingCaregivers(skillIds, startTime, endTime));
    }

    @PostMapping
    public ResponseEntity<CaregiverProfile> createProfile(@RequestBody CaregiverProfile profile) {
        return ResponseEntity.ok(caregiverProfileRepository.save(profile));
    }

    @PostMapping("/{id}/skills/{skillId}")
    public ResponseEntity<CaregiverProfile> addSkillToCaregiver(
            @PathVariable("id") UUID caregiverId,
            @PathVariable("skillId") UUID skillId
    ) {
        CaregiverProfile profile = caregiverProfileRepository.findById(caregiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver profile not found"));
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found"));

        profile.getSkills().add(skill);
        return ResponseEntity.ok(caregiverProfileRepository.save(profile));
    }

    @PostMapping("/{id}/slots")
    public ResponseEntity<AvailabilitySlot> addSlot(
            @PathVariable("id") UUID caregiverId,
            @RequestBody AvailabilitySlot slot
    ) {
        CaregiverProfile profile = caregiverProfileRepository.findById(caregiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver profile not found"));

        if (slot.getStartTime() == null || slot.getEndTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime and endTime are required");
        }
        if (!slot.getEndTime().isAfter(slot.getStartTime())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "endTime must be after startTime (got start=" + slot.getStartTime() + ", end=" + slot.getEndTime() + ")"
            );
        }

        slot.setCaregiverProfile(profile);
        return ResponseEntity.status(HttpStatus.CREATED).body(availabilitySlotRepository.save(slot));
    }

    @GetMapping("/{id}/slots")
    public ResponseEntity<List<AvailabilitySlot>> getOpenSlots(
            @PathVariable("id") UUID caregiverId,
            @RequestParam(value = "status", required = false, defaultValue = "OPEN") SlotStatus status
    ) {
        if (!caregiverProfileRepository.existsById(caregiverId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver profile not found");
        }
        return ResponseEntity.ok(
                availabilitySlotRepository.findByCaregiverProfileCaregiverIdAndStatus(caregiverId, status)
        );
    }

    @PutMapping("/{id}/documents")
    public ResponseEntity<CaregiverProfile> updateDocuments(
            @PathVariable("id") UUID caregiverId,
            @RequestBody DocumentUpdateRequest request
    ) {
        CaregiverProfile profile = caregiverProfileRepository.findById(caregiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver profile not found"));

        if (request.getIdProofUrl() != null) profile.setIdProofUrl(request.getIdProofUrl());
        if (request.getMedicalCertUrl() != null) profile.setMedicalCertUrl(request.getMedicalCertUrl());

        return ResponseEntity.ok(caregiverProfileRepository.save(profile));
    }

    @lombok.Data
    public static class DocumentUpdateRequest {
        private String idProofUrl;
        private String medicalCertUrl;
    }
}
