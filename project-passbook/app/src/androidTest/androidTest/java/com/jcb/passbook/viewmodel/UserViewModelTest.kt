package com.jcb.passbook.viewmodel

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcb.passbook.R
import com.jcb.passbook.viewmodel.AuthState.Error
//import com.jcb.passbook.viewmodel.RegistrationState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class UserViewModelTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var userViewModel: UserViewModel
    private lateinit var context: Context

    companion object {
        const val SHORT_PASSWORD = "short"
        const val VALID_PASSWORD = "StrongPassword123!"
        const val TEST_USERNAME = "testuser"
        const val VALID_USERNAME = "validuser"
    }

    @Before
    fun setup() {
        hiltRule.inject()
        ActivityScenario.launch(ComponentActivity::class.java).onActivity { activity ->
            userViewModel = ViewModelProvider(activity)[UserViewModel::class.java]
            context = activity
        }
    }

    @Test
    fun registrationFailsWithShortPassword() = runTest {
        userViewModel.register(TEST_USERNAME, SHORT_PASSWORD)
        val state = userViewModel.registrationState.first { it != RegistrationState.Idle }
        val expectedMessageId = R.string.error_password_length
        // Get localized string for more portable asserts if desired:
        // val expectedMessage = context.getString(expectedMessageId)

        assertTrue(state is Error && (state as Error).messageId == expectedMessageId)
    }

    @Test
    fun registrationSucceedsWithValidCredentials() = runTest {
        userViewModel.register(VALID_USERNAME, VALID_PASSWORD)
        val state = userViewModel.registrationState.first { it != RegistrationState.Idle }
        assertEquals(RegistrationState.Success, state)
    }
}
