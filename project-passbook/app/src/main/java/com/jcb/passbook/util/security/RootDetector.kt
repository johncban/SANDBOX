package com.jcb.passbook.util.security

import android.content.Context
import com.scottyab.rootbeer.RootBeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Legacy RootDetector with enhanced integration support.
 * Provides backward compatibility while integrating with enhanced security components.
 * 
 * For new implementations, use EnhancedRootDetector instead.
 */
object RootDetector {
    private const val TAG = "RootDetector"
    
    /**
     * Simple root detection using RootBeer library.
     * Maintains backward compatibility with existing code.
     */
    fun isDeviceRooted(context: Context): Boolean {
        return try {
            val rootBeer = RootBeer(context)
            val isRooted = rootBeer.isRooted
            
            if (isRooted) {
                Timber.w("$TAG: Legacy root detection - Device is rooted")
            } else {
                Timber.d("$TAG: Legacy root detection - Device is not rooted")
            }
            
            isRooted
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Legacy root detection failed")
            // Default to not rooted if detection fails
            false
        }
    }
    
    /**
     * Enhanced root detection using the new EnhancedRootDetector.
     * This method provides better detection capabilities and detailed logging.
     * 
     * @param context Android context
     * @param enhancedRootDetector Instance of EnhancedRootDetector (optional)
     * @return True if device is rooted, false otherwise
     */
    suspend fun isDeviceRootedEnhanced(
        context: Context,
        enhancedRootDetector: EnhancedRootDetector? = null
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (enhancedRootDetector != null) {
            try {
                val result = enhancedRootDetector.performEnhancedRootDetection(context)
                Timber.i("$TAG: Enhanced root detection completed - Rooted: ${result.isRooted}, Severity: ${result.severity}")
                result.isRooted
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Enhanced root detection failed, falling back to legacy detection")
                isDeviceRooted(context)
            }
        } else {
            // Fallback to legacy detection if enhanced detector not available
            Timber.w("$TAG: Enhanced detector not available, using legacy detection")
            isDeviceRooted(context)
        }
    }
    
    /**
     * Get detailed root detection information.
     * This method provides comprehensive root detection results including
     * detection methods, severity, and user interaction options.
     * 
     * @param context Android context
     * @param enhancedRootDetector Instance of EnhancedRootDetector
     * @return RootDetectionResult with detailed information
     */
    suspend fun getDetailedRootDetection(
        context: Context,
        enhancedRootDetector: EnhancedRootDetector
    ): EnhancedRootDetector.RootDetectionResult? = withContext(Dispatchers.IO) {
        return@withContext try {
            enhancedRootDetector.performEnhancedRootDetection(context)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to get detailed root detection")
            null
        }
    }
    
    /**
     * Migration helper method for existing code.
     * Provides a simple way to upgrade from legacy to enhanced detection.
     */
    @JvmStatic
    fun createEnhancedDetector(auditLogger: com.jcb.passbook.util.audit.AuditLogger): EnhancedRootDetector {
        return EnhancedRootDetector(auditLogger)
    }
}