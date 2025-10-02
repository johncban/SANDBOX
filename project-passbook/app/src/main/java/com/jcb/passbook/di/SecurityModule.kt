package com.jcb.passbook.di

import com.jcb.passbook.util.audit.AuditLogger
import com.jcb.passbook.util.logging.DebugLogger
import com.jcb.passbook.util.security.EnhancedRootDetector
import com.jcb.passbook.util.security.EnhancedSecurityManager
import com.jcb.passbook.util.security.SecurityDialogManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for enhanced security components.
 * Provides dependency injection for security-related classes.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideEnhancedRootDetector(
        auditLogger: AuditLogger
    ): EnhancedRootDetector {
        return EnhancedRootDetector(auditLogger)
    }

    @Provides
    @Singleton
    fun provideSecurityDialogManager(
        auditLogger: AuditLogger
    ): SecurityDialogManager {
        return SecurityDialogManager(auditLogger)
    }

    @Provides
    @Singleton
    fun provideEnhancedSecurityManager(
        enhancedRootDetector: EnhancedRootDetector,
        securityDialogManager: SecurityDialogManager,
        auditLogger: AuditLogger
    ): EnhancedSecurityManager {
        return EnhancedSecurityManager(
            enhancedRootDetector = enhancedRootDetector,
            securityDialogManager = securityDialogManager,
            auditLogger = auditLogger
        )
    }

    @Provides
    @Singleton
    fun provideDebugLogger(
        auditLogger: AuditLogger
    ): DebugLogger {
        return DebugLogger(auditLogger)
    }
}