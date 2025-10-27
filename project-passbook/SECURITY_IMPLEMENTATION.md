# Passbook Security Implementation Guide

This document outlines the comprehensive security enhancements implemented in the Passbook password manager application.

## Overview

The security implementation enforces a **session-based vault access model** where:
- All vault access requires biometric or password authentication
- Database encryption keys are ephemeral and session-derived
- No persistent storage of database passphrases
- Comprehensive audit logging with integrity verification
- Runtime security detection and automatic lockout

## Architecture Components

### 1. Session Management (`security/session/`)

#### `SessionManager.kt`
- **Purpose**: Central session state management
- **Key Features**:
  - Ephemeral session keys (never persisted)
  - Background timeout enforcement (30 seconds)
  - Secure window flag management
  - Emergency lock capabilities

#### `SessionKeyProvider.kt`
- **Purpose**: Controlled access to session materials
- **Key Features**:
  - Session validation before vault operations
  - Exception-based access control
  - Session ID provision for audit correlation

### 2. Cryptographic Components (`security/crypto/`)

#### `SessionPassphraseManager.kt`
- **Purpose**: Ephemeral database key derivation
- **Key Features**:
  - Biometric-protected master seed storage
  - Argon2id key derivation (64MB memory, 3 iterations)
  - Per-session salt generation
  - Android Keystore integration with `setUserAuthenticationRequired(true)`

**Security Model**:
1. Master seed (32 bytes) encrypted with keystore key requiring biometric
2. On unlock: decrypt seed → generate fresh salt → derive session key via Argon2
3. Session key used for SQLCipher database encryption
4. All materials cleared on lock/timeout

### 3. Database Access (`data/local/database/`)

#### `DatabaseProvider.kt`
- **Purpose**: Session-gated SQLCipher database instances
- **Key Features**:
  - Database creation only with active session passphrase
  - Automatic closure on session lock
  - Session correlation and validation

**Flow**:
```
Vault Access Request → SessionKeyProvider.requireSessionPassphrase() → DatabaseProvider.getDatabase() → SQLCipher Room Instance
```

### 4. Authentication UI (`presentation/ui/screens/auth/`)

#### `UnlockScreen.kt` + `UnlockViewModel.kt`
- **Purpose**: Biometric-first vault unlocking
- **Key Features**:
  - Biometric authentication with `BIOMETRIC_STRONG`
  - Password fallback using same key derivation
  - Comprehensive error handling and audit logging

### 5. Security Detection (`security/detection/`)

#### Enhanced `SecurityManager.kt`
- **Detects**: Root, debugging, emulator, suspicious properties
- **Response**: Emergency session lock and app exit
- **Monitoring**: Periodic background checks

### 6. Audit System (`security/audit/`)

#### `AuditLogger.kt`
- **Features**: Security event logging with session correlation
- **Integrity**: HMAC-SHA256 checksums (planned enhancement)
- **Events**: Authentication, data access, security violations

## Security Guarantees

### ✅ **Biometric-Gated Access**
- Vault access requires biometric or master password authentication
- Background timeout forces re-authentication (30s default)
- Process death requires fresh unlock

### ✅ **Ephemeral Database Keys**
- Database passphrases never persisted to storage
- Fresh derivation on each unlock using Argon2id
- Session keys cleared from memory on lock

### ✅ **Session Isolation**
- Each unlock creates new session with unique ID
- Session state tracked and audited
- Automatic lockout on security events

### ✅ **Runtime Protection**
- Root detection and response
- Debug/tamper detection
- Screenshot prevention when unlocked (`FLAG_SECURE`)

### ✅ **Comprehensive Auditing**
- All security events logged with session correlation
- Device fingerprinting and app version tracking
- Integrity checksums for tamper detection

## Usage Flow

### Initial Setup
1. App generates and encrypts master seed with biometric-required keystore key
2. User enrollment creates biometric template linkage

### Unlock Process
1. App starts in `LOCKED` state, shows `UnlockScreen`
2. User authenticates via biometric or password
3. `SessionPassphraseManager` derives ephemeral session key
4. `SessionManager` transitions to `UNLOCKED` state
5. `DatabaseProvider` creates SQLCipher database with session key
6. Navigation proceeds to vault (`ItemListScreen`)

### Runtime Security
1. `SecurityManager` monitors for compromise indicators
2. Background timeout tracked via lifecycle callbacks
3. Security violations trigger emergency lock and audit

### Lock Triggers
- Manual lock request
- Background timeout (30s)
- Process death
- Security event detection
- Biometric lockout

## Testing

### Unit Tests
- `SessionManagerTest.kt`: Session lifecycle and security
- Additional tests for crypto, database, and UI components

### Security Testing
1. **Session Isolation**: Verify keys cleared on lock
2. **Timeout Enforcement**: Background timer accuracy
3. **Biometric Gating**: Authentication requirements
4. **Database Access**: Session validation enforcement

## Configuration

### Timeouts
```kotlin
// SessionManager.kt
const val BACKGROUND_TIMEOUT_MS = 30_000L // 30 seconds
```

### Argon2 Parameters
```kotlin
// SessionPassphraseManager.kt
const val ARGON2_MEMORY_KB = 65536    // 64MB
const val ARGON2_ITERATIONS = 3
const val ARGON2_PARALLELISM = 1
```

### Biometric Requirements
```kotlin
// BiometricHelper.kt
BiometricManager.Authenticators.BIOMETRIC_STRONG
setUserAuthenticationValidityDurationSeconds(0) // Per-use
```

## Security Considerations

### Threat Model
- **Malware**: Runtime detection and lockout
- **Physical Access**: Biometric requirements and screen recording prevention
- **Memory Dumps**: Ephemeral keys with active zeroization
- **Persistence Attacks**: No persistent key material

### Limitations
- Biometric spoofing (mitigated by `BIOMETRIC_STRONG`)
- Advanced persistent threats with root access
- Hardware-level attacks on secure elements

### Future Enhancements
- Hardware Security Module (HSM) integration
- Remote attestation and device integrity
- Multi-factor authentication options
- Advanced tamper detection

## Migration from Previous Version

1. **Database Rekey**: Existing persistent keys migrated to ephemeral model
2. **Biometric Setup**: Users prompted to enroll biometrics
3. **Audit Migration**: Historical audit entries maintained

## Troubleshooting

### Common Issues
1. **Biometric Unavailable**: Falls back to password authentication
2. **Session Lock Loops**: Check SecurityManager detection false positives
3. **Database Access Errors**: Verify session state and key derivation

### Debug Logging
```kotlin
// Enable security event logging
Timber.plant(Timber.DebugTree())
```

## Compliance

This implementation addresses:
- **OWASP Mobile Top 10**: Cryptographic failures, authentication weaknesses
- **NIST Cybersecurity Framework**: Identify, protect, detect, respond
- **Security by Design**: Minimal attack surface, fail-secure defaults

---

**Security Contact**: For security issues, please follow responsible disclosure.
