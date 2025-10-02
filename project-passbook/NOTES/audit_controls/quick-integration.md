# Quick Integration Guide - Adding Audit Controls to Existing Project

## File Changes Required

### 1. New Files to Create

```
app/src/main/java/com/jcb/passbook/room/AuditEntry.kt
app/src/main/java/com/jcb/passbook/room/AuditDao.kt
app/src/main/java/com/jcb/passbook/util/audit/AuditLogger.kt
app/src/main/java/com/jcb/passbook/repository/AuditRepository.kt
app/src/main/java/com/jcb/passbook/util/audit/SecurityAuditManager.kt
```

### 2. Files to Modify

**AppDatabase.kt** - Add audit table and migration:
```kotlin
@Database(entities = [Item::class, User::class, AuditEntry::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao
    
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            // Add audit table schema
        }
    }
}
```

**AppModule.kt** - Add audit dependencies:
```kotlin
@Provides
fun provideAuditDao(db: AppDatabase): AuditDao = db.auditDao()

@Provides
@Singleton
fun provideAuditLogger(auditDao: AuditDao, @ApplicationContext context: Context): AuditLogger = 
    AuditLogger(auditDao, context)
```

**UserViewModel.kt** - Add audit logging to authentication:
```kotlin
class UserViewModel @Inject constructor(
    // existing parameters
    private val auditLogger: AuditLogger
) {
    fun login(username: String, password: String) {
        // existing login logic
        auditLogger.logAuthentication(username, AuditEventType.LOGIN, outcome)
    }
}
```

**ItemViewModel.kt** - Add audit logging to CRUD operations:
```kotlin
class ItemViewModel @Inject constructor(
    // existing parameters  
    private val auditLogger: AuditLogger
) {
    fun insert(itemName: String, plainTextPassword: String) {
        // existing insert logic
        auditLogger.logDataAccess(userId, username, AuditEventType.CREATE, "ITEM", itemId, "Created item")
    }
}
```

**CoreApp.kt** - Initialize audit system:
```kotlin
@HiltAndroidApp
class CoreApp : Application() {
    @Inject lateinit var auditLogger: AuditLogger
    @Inject lateinit var securityAuditManager: SecurityAuditManager
    
    override fun onCreate() {
        super.onCreate()
        SecurityManager.initializeAuditing(auditLogger)
        securityAuditManager.startSecurityMonitoring()
    }
}
```

**SecurityManager.kt** - Add audit integration:
```kotlin
object SecurityManager {
    lateinit var auditLogger: AuditLogger
    
    fun checkRootStatus(context: Context, onCompromised: (() -> Unit)? = null) {
        if (rootDetected) {
            auditLogger.logSecurityEvent("Root detected", "CRITICAL")
        }
    }
}
```

### 3. Build Configuration

**build.gradle.kts** - Update database version:
```kotlin
android {
    defaultConfig {
        // Update if using Room schema export
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }
}
```

## Implementation Steps

### Step 1: Create Core Audit Components (30 minutes)
1. Copy `AuditEntry.kt` entity class
2. Copy `AuditDao.kt` interface  
3. Copy `AuditLogger.kt` service class

### Step 2: Update Database Schema (15 minutes)
1. Update `AppDatabase.kt` with new entity and migration
2. Add `auditDao()` abstract method
3. Update database version from 2 to 3

### Step 3: Add Dependency Injection (10 minutes)
1. Update `AppModule.kt` with audit providers
2. Ensure proper singleton scoping

### Step 4: Integrate with ViewModels (20 minutes)
1. Add `AuditLogger` injection to `UserViewModel`
2. Add audit calls to login/logout/register methods
3. Add `AuditLogger` injection to `ItemViewModel`  
4. Add audit calls to CRUD operations

### Step 5: Initialize System (10 minutes)
1. Update `CoreApp.kt` with audit initialization
2. Update `SecurityManager.kt` with audit integration

### Step 6: Test Implementation (30 minutes)
1. Run app and verify database migration
2. Test login/logout audit logging
3. Test password CRUD audit logging
4. Verify security event logging

## Testing Checklist

### ✅ Database Migration
- App starts without crashes after update
- AuditEntry table exists in database
- All indexes are created properly

### ✅ Authentication Audit
- Login success events logged
- Login failure events logged  
- Registration events logged
- Logout events logged

### ✅ Data Access Audit
- Password creation logged
- Password viewing logged
- Password updates logged
- Password deletion logged

### ✅ Security Event Audit
- Root detection logged
- Security compromise logged
- Debug detection logged

### ✅ Performance Validation
- UI remains responsive during audit logging
- No visible performance impact
- Background logging works properly

## Verification Commands

### Check Audit Table Structure
```sql
.schema audit_entry
```

### Sample Audit Queries
```sql
-- Recent login events
SELECT * FROM audit_entry WHERE eventType = 'LOGIN' ORDER BY timestamp DESC LIMIT 10;

-- Failed authentication attempts  
SELECT * FROM audit_entry WHERE eventType = 'AUTH_FAILURE' ORDER BY timestamp DESC;

-- Critical security events
SELECT * FROM audit_entry WHERE securityLevel = 'CRITICAL' ORDER BY timestamp DESC;

-- User activity summary
SELECT username, eventType, COUNT(*) as count 
FROM audit_entry 
WHERE userId IS NOT NULL 
GROUP BY username, eventType;
```

## Security Validation

### ✅ Encryption Verification
- Audit entries stored in encrypted SQLCipher database
- Checksums properly generated for integrity protection
- No sensitive data in plain text logs

### ✅ Access Control Verification
- Only authorized components can write audit logs
- Audit logs are append-only (no update/delete from app)
- User isolation maintained in audit entries

### ✅ Compliance Verification
- All HIPAA-required events are logged
- Timestamps are accurate and consistent
- User identification is complete
- Device context is captured

## Troubleshooting Common Issues

### Database Migration Fails
```kotlin
// Enable fallback for development only
if (BuildConfig.DEBUG) {
    builder.fallbackToDestructiveMigration()
}
```

### Audit Logging Performance Issues
```kotlin
// Verify async scope is used
private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

### Missing Audit Entries
```kotlin
// Check for proper exception handling
try {
    auditDao.insert(auditEntry)
} catch (e: Exception) {
    Timber.e(e, "Audit logging failed")
    // Don't let audit failures crash the app
}
```

## Production Deployment Notes

1. **Test thoroughly** with database migration on various Android versions
2. **Monitor performance** to ensure audit logging doesn't impact user experience
3. **Validate compliance** with HIPAA audit requirements before production
4. **Set up monitoring** for audit system health and storage usage
5. **Plan for audit log retention** and archival procedures
6. **Document audit procedures** for compliance reviews