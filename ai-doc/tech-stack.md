
# Patchamomma MVP Architecture & Database Documentation

## 1. Executive Summary & Problem Statement
Currently, families must manually handpick caregivers or nurses to look after aged or medically challenged adults. This manual vetting process lacks a standardized sense of security, involves fragmented communication, and becomes mentally exhausting for families under stress.

**Patchamomma** solves this problem by providing an application that allows care recipients to input specific medical and care requirements, helping safely match, verify, and secure the right caregiver with minimal effort.

---

## 2. Finalized Tech Stack & Cloud Infrastructure

The Patchamomma MVP leverages a serverless, cost-optimized tech stack powered by Google Cloud Platform (GCP) and Flutter.

```
+-------------------------------------------------------------------------------------------------+
|                                    MOBILE FRONTEND LAYER                                        |
|                     Flutter (Dart) Mobile App (iOS & Android)                                   |
|       - Caregiver: Profile setup, ID/Cert image picker, Availability management                 |
|       - Care Recipient: Skill filtering, Slot selection, Caregiver discovery                    |
+-------------------------------------------------------------------------------------------------+
                                                  │
                                            HTTPS / REST
                                                  ▼
+-------------------------------------------------------------------------------------------------+
|                                  SERVERLESS BACKEND LAYER                                       |
|                Java 17/21 (Spring Boot 3) hosted on Google Cloud Run                            |
|       - Auth Controller: Validates Firebase JWTs and enforces RBAC                              |
|       - Caregiver Controller: Handles profile creation & document uploads                       |
|       - Booking Controller: Matches skill criteria and executes slot lock transactions           |
|       - Admin Controller: Approves/Rejects pending caregiver applications                       |
+-------------------------------------------------------------------------------------------------+
                                       │                    │
                   ┌───────────────────┴────────────────────┴───────────────────┐
                   ▼                                                            ▼
+------------------------------------+                        +------------------------------------+
|          DATABASE LAYER            |                        |           STORAGE LAYER            |
|       Cloud SQL (PostgreSQL)       |                        |     Cloud Storage for Firebase     |
| - Users & Caregiver Profiles       |                        | - Private bucket for ID Scans      |
| - Skills Matrix (Junction tables)  |                        | - Medical Certifications (PDF/Img) |
| - Open / Booked Slots Ledger       |                        | - Served via Signed URLs           |
+------------------------------------+                        +------------------------------------+
```

### Component Breakdown

| Layer | Technology | Key Purpose in MVP |
| :--- | :--- | :--- |
| **Mobile App (Frontend)** | **Flutter (Dart)** | Single cross-platform codebase for iOS and Android. Handles patient skill-filtering UI, caregiver availability calendars, and document capture/upload. |
| **REST API (Backend)** | **Java 17/21 (Spring Boot 3)** | Enterprise backend managing business logic, JWT security parsing, slot booking transactions, and database operations. |
| **Serverless Compute** | **Google Cloud Run** | Containerizes and hosts the Spring Boot REST API. Automatically scales to 0 instances when idle to conserve GCP credits. |
| **Relational Database** | **Cloud SQL (PostgreSQL)** | Maintains strict transactional integrity (ACID) for caregiver skill matrices, time slot availability, and booking status. |
| **File Storage** | **Cloud Storage for Firebase** | Secure, encrypted bucket for sensitive documents (ID scans, medical certifications) accessed via signed URLs. |
| **Auth & Authorization** | **Firebase Auth + Spring Security** | Manages user credentials and issues JWTs with embedded user roles (`ROLE_CAREGIVER`, `ROLE_RECIPIENT`, `ROLE_ADMIN`). |
| **Secret Security** | **Secret Manager** | Secures database credentials and JWT private keys outside version control. |
| **Verification Workflow** | **Manual Admin Flow** | Zero-AI manual audit workflow for human review of caregiver credentials prior to active account status transition. |

---

## 3. Core Workflow Architecture

### 3.1 Caregiver Onboarding & Manual Verification Pipeline
1. **Registration & Document Upload:** The caregiver creates an account using Flutter and uploads ID proof and medical certifications.
2. **Private Storage & Pending Lock:** Files stream through Spring Boot into Cloud Storage for Firebase. The caregiver profile in Cloud SQL is flagged as `verification_status = 'PENDING'`.
3. **Admin Audit:** Administrators access pending files via secure, short-lived signed URLs.
4. **Activation:** Upon admin approval, `verification_status` updates to `'APPROVED'`, making the profile and skill matrix active and searchable.

### 3.2 Skill-Based Discovery & Booking Engine
1. **Search & Filter:** Care recipients select required skill tags (e.g., *IV Injection*, *Dementia Care*, *Palliative*) and desired time slots.
2. **SQL Query Execution:**
   ```sql
   SELECT DISTINCT cp.*, u.full_name 
   FROM caregiver_profiles cp
   JOIN users u ON cp.user_id = u.user_id
   JOIN caregiver_skills cs ON cp.caregiver_id = cs.caregiver_id
   JOIN availability_slots s ON cp.caregiver_id = s.caregiver_id
   WHERE cp.status = 'APPROVED'
     AND cs.skill_id IN (:selectedSkillIds)
     AND s.status = 'OPEN'
     AND s.start_time >= :requestStartTime 
     AND s.end_time <= :requestEndTime;
   ```
3. **Atomic Booking Reservation:** Spring Boot executes a database transaction (`SELECT ... FOR UPDATE`) on the selected slot, setting status to `'BOOKED'` to prevent double-booking.

---

## 4. Production Database Schema (PostgreSQL DDL)

```sql
-- PostgreSQL Data Definition Language (DDL) Script for Patchamomma MVP

-- 1. Create Enum Types for Statuses and Roles
CREATE TYPE user_role AS ENUM ('ROLE_RECIPIENT', 'ROLE_CAREGIVER', 'ROLE_ADMIN');
CREATE TYPE verification_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
CREATE TYPE slot_status AS ENUM ('OPEN', 'BOOKED', 'CANCELLED');
CREATE TYPE booking_status AS ENUM ('CONFIRMED', 'COMPLETED', 'CANCELLED');

-- 2. Users Table (Core Identity Mapping)
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid VARCHAR(128) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    role user_role NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Caregiver Profiles Table
CREATE TABLE caregiver_profiles (
    caregiver_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    id_proof_url VARCHAR(512),
    medical_cert_url VARCHAR(512),
    status verification_status DEFAULT 'PENDING' NOT NULL,
    bio TEXT,
    hourly_rate NUMERIC(10, 2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Skills Master Table
CREATE TABLE skills (
    skill_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_name VARCHAR(100) UNIQUE NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT
);

-- 5. Caregiver-Skill Junction Table (Matrix Mapping)
CREATE TABLE caregiver_skills (
    caregiver_id UUID NOT NULL REFERENCES caregiver_profiles(caregiver_id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(skill_id) ON DELETE CASCADE,
    PRIMARY KEY (caregiver_id, skill_id)
);

-- 6. Availability Slots Table
CREATE TABLE availability_slots (
    slot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caregiver_id UUID NOT NULL REFERENCES caregiver_profiles(caregiver_id) ON DELETE CASCADE,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status slot_status DEFAULT 'OPEN' NOT NULL,
    CONSTRAINT chk_time_window CHECK (end_time > start_time)
);

-- 7. Bookings Ledger Table
CREATE TABLE bookings (
    booking_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id UUID UNIQUE NOT NULL REFERENCES availability_slots(slot_id) ON DELETE RESTRICT,
    recipient_id UUID NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    caregiver_id UUID NOT NULL REFERENCES caregiver_profiles(caregiver_id) ON DELETE RESTRICT,
    status booking_status DEFAULT 'CONFIRMED' NOT NULL,
    booked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Database Performance Indexes
CREATE INDEX idx_users_firebase_uid ON users(firebase_uid);
CREATE INDEX idx_caregiver_status ON caregiver_profiles(status);
CREATE INDEX idx_slots_caregiver_search ON availability_slots(caregiver_id, start_time, status);
CREATE INDEX idx_caregiver_skills_skill ON caregiver_skills(skill_id);
```

---

## 5. Summary of Scope Exclusions for MVP
* **AI Agentic Workflows:** Document verification is kept 100% manual for human oversight and credit conservation.
* **BigQuery & Looker Analytics:** Streaming data pipelines deferred until post-launch user traction.
* **Payment Gateway Integration:** Payment processing flow deferred to Phase 2.