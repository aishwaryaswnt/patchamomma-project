# Patchamomma Caregiver Matching Platform

**Patchamomma** is a secure, serverless application for care recipients and families to find, filter, and book verified caregivers and nurses tailored to specific medical requirements.

---

## Project Architecture & Directory Layout

```
patchamomma/
├── README.md                      # Main project documentation & entrypoint
├── ai-doc/                        # Project documentation & specs
│   ├── tech-stack.md              # Technical architecture & PostgreSQL schema specification
│   └── environment-setup.md       # Developer guide & mobile simulator setup instructions
├── backend/                       # Java 21 + Spring Boot 3 REST API
│   ├── pom.xml                    # Maven build configuration
│   ├── docker-compose.yml         # Local PostgreSQL container configuration
│   └── src/main/java/             # Spring Boot REST Controllers, Entities & Repositories
└── frontend/                      # Flutter (Dart) Cross-Platform Mobile Application
    ├── pubspec.yaml               # Flutter package configuration
    └── lib/                       # Mobile UI flows (Caregiver, Recipient, Admin Audit)
```

---

## Documentation Quick Links

* [📐 Technical Architecture & Database Schema](file:///Users/rajat/code/patchamomma/ai-doc/tech-stack.md)
* [⚙️ Developer Environment & Mobile Simulator Setup Guide](file:///Users/rajat/code/patchamomma/ai-doc/environment-setup.md)
* [🛑 Start / Stop (local + Cloud Run + Firebase Hosting)](file:///Users/rajat/code/patchamomma/ai-doc/start-stop-guide.md)

---

## Getting Started

### 1. Developer Setup & Prerequisites
Please consult [ai-doc/environment-setup.md](file:///Users/rajat/code/patchamomma/ai-doc/environment-setup.md) for step-by-step installation instructions for:
- Flutter SDK & Mobile Simulators (iOS Simulator via Xcode, Android Emulator via Android Studio)
- Java 21 JDK & Maven
- PostgreSQL setup (Homebrew or Docker Desktop)

### 2. Running the Spring Boot Backend

```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn spring-boot:run
```

The REST API server will run on `http://localhost:8080/api/v1`.

### 3. Running the Flutter Frontend

```bash
cd frontend
flutter run
```
