package com.jcb.passbook

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner for Hilt integration testing.
 * Provides proper application context for dependency injection in tests.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}