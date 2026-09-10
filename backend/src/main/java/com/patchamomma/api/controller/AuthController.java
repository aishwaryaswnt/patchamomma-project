package com.patchamomma.api.controller;

import com.patchamomma.api.model.CaregiverProfile;
import com.patchamomma.api.model.User;
import com.patchamomma.api.model.UserRole;
import com.patchamomma.api.model.VerificationStatus;
import com.patchamomma.api.repository.CaregiverProfileRepository;
import com.patchamomma.api.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user) {
        Optional<User> existing = userRepository.findByEmail(user.getEmail());
        if (existing.isPresent()) {
            User existingUser = existing.get();
            ensureCaregiverProfileExists(existingUser);
            return ResponseEntity.ok(existingUser);
        }
        User saved = userRepository.save(user);
        ensureCaregiverProfileExists(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/login")
    public ResponseEntity<User> loginUser(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found with this email. Please Sign Up first."));
        ensureCaregiverProfileExists(user);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/google")
    public ResponseEntity<User> googleSignIn(@RequestBody GoogleAuthRequest request) {
        Optional<User> existing = userRepository.findByEmail(request.getEmail());
        if (existing.isPresent()) {
            User existingUser = existing.get();
            ensureCaregiverProfileExists(existingUser);
            return ResponseEntity.ok(existingUser);
        }

        User newUser = User.builder()
                .firebaseUid(request.getGoogleId() != null ? request.getGoogleId() : "google_" + System.currentTimeMillis())
                .email(request.getEmail())
                .fullName(request.getFullName() != null ? request.getFullName() : "Google User")
                .role(request.getRole() != null ? request.getRole() : UserRole.ROLE_RECIPIENT)
                .build();

        User saved = userRepository.save(newUser);
        ensureCaregiverProfileExists(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    private void ensureCaregiverProfileExists(User user) {
        if (user.getRole() == UserRole.ROLE_CAREGIVER) {
            Optional<CaregiverProfile> profileOpt = caregiverProfileRepository.findByUserUserId(user.getUserId());
            if (profileOpt.isEmpty()) {
                CaregiverProfile profile = CaregiverProfile.builder()
                        .user(user)
                        .status(VerificationStatus.PENDING)
                        .hourlyRate(new BigDecimal("45.00"))
                        .build();
                caregiverProfileRepository.save(profile);
            }
        }
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    public static class GoogleAuthRequest {
        private String googleId;
        private String email;
        private String fullName;
        private UserRole role;
    }
}
