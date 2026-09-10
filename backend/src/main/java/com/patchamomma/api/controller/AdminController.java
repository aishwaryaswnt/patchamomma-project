package com.patchamomma.api.controller;

import com.patchamomma.api.model.CaregiverProfile;
import com.patchamomma.api.model.VerificationStatus;
import com.patchamomma.api.repository.CaregiverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CaregiverProfileRepository caregiverProfileRepository;

    @GetMapping("/pending-caregivers")
    public ResponseEntity<List<CaregiverProfile>> getPendingCaregivers() {
        return ResponseEntity.ok(caregiverProfileRepository.findByStatus(VerificationStatus.PENDING));
    }

    @PutMapping("/caregivers/{id}/verify")
    public ResponseEntity<CaregiverProfile> updateVerificationStatus(
            @PathVariable("id") UUID caregiverId,
            @RequestParam("status") VerificationStatus status
    ) {
        CaregiverProfile profile = caregiverProfileRepository.findById(caregiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver not found"));

        profile.setStatus(status);
        CaregiverProfile updated = caregiverProfileRepository.save(profile);
        return ResponseEntity.ok(updated);
    }
}
