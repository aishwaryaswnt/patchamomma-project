package com.patchamomma.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shift_care_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ShiftCareLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id", updatable = false, nullable = false)
    private UUID logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caregiver_id", nullable = false)
    private CaregiverProfile caregiverProfile;

    @Column(name = "systolic_bp")
    private Integer systolicBp;

    @Column(name = "diastolic_bp")
    private Integer diastolicBp;

    @Column(name = "blood_sugar_mg_dl")
    private Integer bloodSugarMgDl;

    @Column(name = "medications_given")
    @Builder.Default
    private Boolean medicationsGiven = false;

    @Column(name = "meal_taken")
    @Builder.Default
    private Boolean mealTaken = false;

    @Column(name = "raw_voice_note", columnDefinition = "TEXT")
    private String rawVoiceNote;

    @Column(name = "summary_notes", columnDefinition = "TEXT")
    private String summaryNotes;

    @CreationTimestamp
    @Column(name = "logged_at", updatable = false)
    private OffsetDateTime loggedAt;
}
